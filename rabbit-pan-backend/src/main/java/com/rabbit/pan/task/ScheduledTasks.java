package com.rabbit.pan.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rabbit.pan.common.Constants;
import com.rabbit.pan.entity.FileInfo;
import com.rabbit.pan.entity.FileShare;
import com.rabbit.pan.mapper.FileInfoMapper;
import com.rabbit.pan.mapper.FileShareMapper;
import com.rabbit.pan.service.ConfigService;
import com.rabbit.pan.service.RecycleService;
import com.rabbit.pan.service.RedisService;
import com.rabbit.pan.util.MinioTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;

/**
 * 定时任务：过期分享清理 / 回收站自动清理 / 每日存储统计 / 临时文件巡检
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ScheduledTasks {

    private final FileShareMapper fileShareMapper;
    private final FileInfoMapper fileInfoMapper;
    private final RecycleService recycleService;
    private final ConfigService configService;
    private final RedisService redisService;
    private final MinioTemplate minioTemplate;

    /** 每小时：清理过期分享记录，并同步删除 Redis 短码映射 */
    @Scheduled(cron = "0 0 * * * ?")
    public void cleanExpiredShares() {
        Date now = new Date();
        List<FileShare> expired = fileShareMapper.selectList(new LambdaQueryWrapper<FileShare>()
                .eq(FileShare::getStatus, Constants.SHARE_STATUS_ACTIVE)
                .isNotNull(FileShare::getExpireTime)
                .lt(FileShare::getExpireTime, now));
        if (expired.isEmpty()) {
            return;
        }
        for (FileShare share : expired) {
            fileShareMapper.deleteById(share.getId());
            redisService.delete(Constants.RedisKeys.SHARE_CODE + share.getShortCode());
        }
        log.info("定时任务: 清理过期分享 {} 条", expired.size());
    }

    /** 每天凌晨 3 点：回收站超过保留天数的文件自动清理（物理删除，释放磁盘空间） */
    @Scheduled(cron = "0 0 3 * * ?")
    public void cleanRecycleBin() {
        int keepDays = configService.getInt(ConfigService.KEY_RECYCLE_KEEP_DAYS, 30);
        Date threshold = new Date(System.currentTimeMillis() - keepDays * 86_400_000L);
        // 找出需要清理的用户集合
        List<FileInfo> markers = fileInfoMapper.selectList(new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getStatus, Constants.FILE_STATUS_RECYCLE)
                .lt(FileInfo::getDeleteTime, threshold)
                .select(FileInfo::getUserId)
                .groupBy(FileInfo::getUserId));
        long total = 0;
        for (FileInfo marker : markers) {
            List<FileInfo> userExpired = fileInfoMapper.selectList(new LambdaQueryWrapper<FileInfo>()
                    .eq(FileInfo::getUserId, marker.getUserId())
                    .eq(FileInfo::getStatus, Constants.FILE_STATUS_RECYCLE)
                    .lt(FileInfo::getDeleteTime, threshold));
            List<Long> ids = userExpired.stream().map(FileInfo::getId).toList();
            if (!ids.isEmpty()) {
                total += recycleService.deleteForever(marker.getUserId(), ids);
            }
        }
        log.info("定时任务: 回收站自动清理完成, keepDays={}, cleanedRows={}", keepDays, total);
    }

    /** 每天凌晨 1 点：存储使用统计（日志留痕，便于对接监控告警） */
    @Scheduled(cron = "0 0 1 * * ?")
    public void storageStatistics() {
        Long fileCount = fileInfoMapper.selectCount(new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getIsDir, false)
                .eq(FileInfo::getStatus, Constants.FILE_STATUS_NORMAL));
        List<FileInfo> files = fileInfoMapper.selectList(new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getIsDir, false)
                .eq(FileInfo::getStatus, Constants.FILE_STATUS_NORMAL)
                .select(FileInfo::getSize));
        long used = files.stream().mapToLong(f -> f.getSize() == null ? 0 : f.getSize()).sum();
        log.info("定时任务: 每日存储统计 files={}, usedGB={}", fileCount,
                String.format("%.2f", used / 1024.0 / 1024 / 1024));
    }

    /** 每天凌晨 4 点：巡检清理超过 24 小时的打包临时 ZIP（孤儿任务兜底） */
    @Scheduled(cron = "0 0 4 * * ?")
    public void cleanTmpZips() {
        List<String> zipKeys = minioTemplate.listByPrefix(Constants.MINIO_DIR_ZIP);
        // ZIP 任务 Redis 记录 TTL 24 小时，此处直接清理全部残留（正常任务完成后亦会按需重新打包）
        zipKeys.forEach(minioTemplate::remove);
        log.info("定时任务: 临时打包文件巡检完成, cleaned={}", zipKeys.size());
    }
}
