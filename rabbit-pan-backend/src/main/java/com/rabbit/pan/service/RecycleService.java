package com.rabbit.pan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rabbit.pan.common.BizException;
import com.rabbit.pan.common.Constants;
import com.rabbit.pan.common.ResultCode;
import com.rabbit.pan.entity.FileInfo;
import com.rabbit.pan.mapper.FileInfoMapper;
import com.rabbit.pan.util.MinioTemplate;
import com.rabbit.pan.vo.FileVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 回收站服务：恢复 / 永久删除 / 清空
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecycleService {

    private final FileInfoMapper fileInfoMapper;
    private final FileService fileService;
    private final ConfigService configService;
    private final MinioTemplate minioTemplate;

    /** 回收站分页列表 */
    public Page<FileInfo> list(Long userId, int pageNum, int pageSize, String keyword) {
        LambdaQueryWrapper<FileInfo> wrapper = new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getUserId, userId)
                .eq(FileInfo::getStatus, Constants.FILE_STATUS_RECYCLE);
        if (StringUtils.hasText(keyword)) {
            wrapper.like(FileInfo::getFileName, keyword);
        }
        wrapper.orderByDesc(FileInfo::getDeleteTime);
        return fileInfoMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    public FileVO toVO(FileInfo file) {
        FileVO vo = fileService.toFileVO(file);
        vo.setDeleteTime(file.getDeleteTime());
        int keepDays = configService.getInt(ConfigService.KEY_RECYCLE_KEEP_DAYS, 30);
        if (file.getDeleteTime() != null) {
            long left = keepDays - (System.currentTimeMillis() - file.getDeleteTime().getTime()) / 86_400_000L;
            vo.setRecycleDaysLeft(Math.max(0, left));
        }
        return vo;
    }

    /** 恢复到原路径；原路径不存在则恢复到根目录；重名则提示 */
    @Transactional(rollbackFor = Exception.class)
    public int restore(Long userId, List<Long> fileIds) {
        Date now = new Date();
        int count = 0;
        for (Long id : fileIds) {
            FileInfo file = fileInfoMapper.selectById(id);
            if (file == null || !file.getUserId().equals(userId)
                    || file.getStatus() != Constants.FILE_STATUS_RECYCLE) {
                throw new BizException(ResultCode.FILE_NOT_FOUND);
            }
            Long targetParent = file.getOriginalParentId() == null ? 0L : file.getOriginalParentId();
            // 原父目录已不存在或也在回收站 => 恢复到根目录
            if (targetParent != 0) {
                FileInfo parent = fileInfoMapper.selectById(targetParent);
                if (parent == null || parent.getStatus() != Constants.FILE_STATUS_NORMAL
                        || !parent.getUserId().equals(userId)) {
                    targetParent = 0L;
                }
            }
            // 同名校验（重名则提示，遵循需求文档行为）
            LambdaQueryWrapper<FileInfo> conflict = new LambdaQueryWrapper<FileInfo>()
                    .eq(FileInfo::getUserId, userId)
                    .eq(FileInfo::getParentId, targetParent)
                    .eq(FileInfo::getStatus, Constants.FILE_STATUS_NORMAL)
                    .eq(FileInfo::getFileName, file.getFileName());
            if (fileInfoMapper.selectCount(conflict) > 0) {
                throw new BizException(ResultCode.NAME_CONFLICT,
                        "「" + file.getFileName() + "」恢复位置已存在同名文件，请先处理");
            }
            file.setStatus(Constants.FILE_STATUS_NORMAL);
            file.setParentId(targetParent);
            file.setOriginalParentId(null);
            file.setDeleteTime(null);
            file.setUpdateTime(now);
            fileInfoMapper.updateById(file);
            count++;
        }
        log.info("回收站恢复: userId={}, count={}", userId, count);
        return count;
    }

    /** 永久删除：递归收集子树文件行，物理删除 MinIO 对象与数据库记录 */
    @Transactional(rollbackFor = Exception.class)
    public long deleteForever(Long userId, List<Long> fileIds) {
        List<Long> deleteIds = new ArrayList<>();
        List<String> objectKeys = new ArrayList<>();
        for (Long id : fileIds) {
            FileInfo file = fileInfoMapper.selectById(id);
            if (file == null || !file.getUserId().equals(userId)
                    || file.getStatus() != Constants.FILE_STATUS_RECYCLE) {
                throw new BizException(ResultCode.FILE_NOT_FOUND);
            }
            collectForPurge(file, deleteIds, objectKeys, userId);
        }
        // 物理删除 MinIO 对象
        minioTemplate.removeBatch(objectKeys);
        fileInfoMapper.deleteBatchIds(deleteIds);
        log.info("回收站永久删除: userId={}, rows={}, objects={}", userId, deleteIds.size(), objectKeys.size());
        return deleteIds.size();
    }

    /** 清空当前用户回收站 */
    @Transactional(rollbackFor = Exception.class)
    public long clear(Long userId) {
        List<FileInfo> recycled = fileInfoMapper.selectList(new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getUserId, userId)
                .eq(FileInfo::getStatus, Constants.FILE_STATUS_RECYCLE));
        List<Long> ids = recycled.stream().map(FileInfo::getId).toList();
        return ids.isEmpty() ? 0 : deleteForever(userId, ids);
    }

    private void collectForPurge(FileInfo node, List<Long> deleteIds, List<String> objectKeys, Long userId) {
        if (node.getIsDir()) {
            List<FileInfo> children = fileInfoMapper.selectList(new LambdaQueryWrapper<FileInfo>()
                    .eq(FileInfo::getParentId, node.getId())
                    .eq(FileInfo::getUserId, userId));
            children.forEach(c -> collectForPurge(c, deleteIds, objectKeys, userId));
        } else if (node.getStoragePath() != null) {
            // 存储对象可能被多个文件行引用（秒传/复制），仅最后一处引用时才物理删除
            Long refCount = fileInfoMapper.selectCount(new LambdaQueryWrapper<FileInfo>()
                    .eq(FileInfo::getStoragePath, node.getStoragePath())
                    .ne(FileInfo::getId, node.getId()));
            if (refCount == 0) {
                objectKeys.add(node.getStoragePath());
            }
        }
        deleteIds.add(node.getId());
    }
}
