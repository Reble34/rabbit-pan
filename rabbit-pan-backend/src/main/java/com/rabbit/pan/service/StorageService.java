package com.rabbit.pan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rabbit.pan.common.BizException;
import com.rabbit.pan.common.Constants;
import com.rabbit.pan.common.ResultCode;
import com.rabbit.pan.entity.FileInfo;
import com.rabbit.pan.mapper.FileInfoMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 存储空间服务：用量统计与双重配额校验
 *
 * <p>配额模型（sys_config）：</p>
 * <ul>
 *   <li>系统总存储空间 total_storage_gb（默认 30GB，全用户共享上限）；</li>
 *   <li>用户配额 storage_total（新用户默认 5GB，管理员可单独调整）。</li>
 * </ul>
 * <p>上传落库前校验顺序：个人配额 → 系统总额，任一不足即拒绝。</p>
 */
@Service
@RequiredArgsConstructor
public class StorageService {

    private final FileInfoMapper fileInfoMapper;
    private final ConfigService configService;

    /** 统计用户已用容量（字节，仅统计正常状态文件） */
    public long usedBytes(Long userId) {
        LambdaQueryWrapper<FileInfo> wrapper = new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getUserId, userId)
                .eq(FileInfo::getStatus, Constants.FILE_STATUS_NORMAL)
                .eq(FileInfo::getIsDir, false);
        Long used = fileInfoMapper.selectList(wrapper)
                .stream().mapToLong(f -> f.getSize() == null ? 0L : f.getSize()).sum();
        return used == 0 ? 0L : used;
    }

    /** 用户文件总数 */
    public long fileCount(Long userId) {
        return fileInfoMapper.selectCount(new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getUserId, userId)
                .eq(FileInfo::getStatus, Constants.FILE_STATUS_NORMAL)
                .eq(FileInfo::getIsDir, false));
    }

    /** 系统总存储空间（字节） */
    public long systemTotalBytes() {
        return configService.getInt(ConfigService.KEY_TOTAL_STORAGE_GB, 30) * 1024L * 1024 * 1024;
    }

    /** 系统已用容量（字节，全用户正常文件之和） */
    public long systemUsedBytes() {
        Long used = fileInfoMapper.selectList(new LambdaQueryWrapper<FileInfo>()
                        .eq(FileInfo::getStatus, Constants.FILE_STATUS_NORMAL)
                        .eq(FileInfo::getIsDir, false)
                        .select(FileInfo::getSize))
                .stream().mapToLong(f -> f.getSize() == null ? 0L : f.getSize()).sum();
        return used == 0 ? 0L : used;
    }

    /**
     * 双重配额校验：新增 addBytes 后是否超限
     * <p>1) 个人配额：用户已用 + addBytes ≤ 用户 storage_total；</p>
     * <p>2) 系统总额：全系统已用 + addBytes ≤ total_storage_gb。</p>
     */
    public void checkQuota(Long userId, long userTotalBytes, long addBytes) {
        if (addBytes <= 0) {
            return;
        }
        long used = usedBytes(userId);
        if (used + addBytes > userTotalBytes) {
            throw new BizException(ResultCode.QUOTA_EXCEEDED);
        }
        if (systemUsedBytes() + addBytes > systemTotalBytes()) {
            throw new BizException(ResultCode.SYSTEM_STORAGE_FULL);
        }
    }
}
