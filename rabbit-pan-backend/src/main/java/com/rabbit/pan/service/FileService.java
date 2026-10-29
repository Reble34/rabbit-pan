package com.rabbit.pan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rabbit.pan.common.BizException;
import com.rabbit.pan.common.Constants;
import com.rabbit.pan.common.ResultCode;
import com.rabbit.pan.dto.FolderCreateDTO;
import com.rabbit.pan.dto.MoveDTO;
import com.rabbit.pan.entity.FileInfo;
import com.rabbit.pan.mapper.FileInfoMapper;
import com.rabbit.pan.vo.FileVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;

/**
 * 文件与目录服务：列表/搜索/新建/重命名/移动/复制/软删除
 *
 * <p>核心安全约束：所有操作强制校验文件所属权（userId 匹配），杜绝水平越权。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    /** 允许的排序字段白名单 */
    private static final Map<String, String> SORT_COLUMNS = Map.of(
            "fileName", "file_name", "size", "size", "updateTime", "update_time", "createTime", "create_time");

    private final FileInfoMapper fileInfoMapper;

    // ==================== 查询 ====================

    /**
     * 目录列表或全局搜索
     * <p>带 keyword/类型/时间条件时为全局搜索（递归），否则按 parentId 列目录。</p>
     */
    public Page<FileInfo> list(Long userId, Long parentId, int pageNum, int pageSize,
                               String sortField, String sortOrder, String keyword,
                               String type, Date startTime, Date endTime) {
        LambdaQueryWrapper<FileInfo> wrapper = new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getUserId, userId)
                .eq(FileInfo::getStatus, Constants.FILE_STATUS_NORMAL);
        boolean searching = StringUtils.hasText(keyword) || StringUtils.hasText(type)
                || startTime != null || endTime != null;
        if (searching) {
            if (StringUtils.hasText(keyword)) {
                wrapper.like(FileInfo::getFileName, keyword);
            }
            if (StringUtils.hasText(type)) {
                wrapper.eq(FileInfo::getFileType, type);
            }
            if (startTime != null) {
                wrapper.ge(FileInfo::getUpdateTime, startTime);
            }
            if (endTime != null) {
                wrapper.le(FileInfo::getUpdateTime, endTime);
            }
        } else {
            wrapper.eq(FileInfo::getParentId, parentId == null ? 0L : parentId);
        }
        // 排序（白名单防注入）：文件夹始终排在文件前面
        String column = SORT_COLUMNS.getOrDefault(sortField, "update_time");
        boolean asc = "asc".equalsIgnoreCase(sortOrder);
        wrapper.orderByDesc("is_dir");
        if (asc) {
            wrapper.orderByAsc(column);
        } else {
            wrapper.orderByDesc(column);
        }
        return fileInfoMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
    }

    public FileVO toFileVO(FileInfo file) {
        FileVO vo = new FileVO();
        vo.setId(file.getId());
        vo.setParentId(file.getParentId());
        vo.setFileName(file.getFileName());
        vo.setIsDir(file.getIsDir());
        vo.setSize(file.getSize());
        vo.setFileType(file.getFileType());
        vo.setExt(file.getExt());
        vo.setMd5(file.getMd5());
        vo.setCreateTime(file.getCreateTime());
        vo.setUpdateTime(file.getUpdateTime());
        return vo;
    }

    /** 公开场景（分享浏览）视图：不下发 MD5，避免配合秒传机制做侧信道引用 */
    public FileVO toPublicFileVO(FileInfo file) {
        FileVO vo = toFileVO(file);
        vo.setMd5(null);
        return vo;
    }

    // ==================== 写操作 ====================

    /** 新建文件夹 */
    public FileInfo createFolder(Long userId, FolderCreateDTO dto) {
        validateParent(userId, dto.getParentId());
        String safeName = com.rabbit.pan.util.FileTypeUtil.sanitizeText(dto.getFileName());
        if (safeName.isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "文件夹名称不能为空");
        }
        checkSiblingNameConflict(userId, dto.getParentId(), safeName, null);
        FileInfo folder = new FileInfo();
        folder.setUserId(userId);
        folder.setParentId(dto.getParentId());
        folder.setFileName(safeName);
        folder.setIsDir(true);
        folder.setSize(0L);
        folder.setFileType(null);
        folder.setExt(null);
        folder.setStatus(Constants.FILE_STATUS_NORMAL);
        folder.setCreateTime(new Date());
        folder.setUpdateTime(new Date());
        fileInfoMapper.insert(folder);
        log.info("新建文件夹: userId={}, parentId={}, name={}", userId, dto.getParentId(), folder.getFileName());
        return folder;
    }

    /** 重命名 */
    public void rename(Long userId, Long fileId, String newFileName) {
        FileInfo file = getOwned(userId, fileId);
        newFileName = com.rabbit.pan.util.FileTypeUtil.sanitizeText(newFileName);
        if (newFileName.isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "文件名不能为空");
        }
        if (newFileName.equals(file.getFileName())) {
            return;
        }
        checkSiblingNameConflict(userId, file.getParentId(), newFileName, fileId);
        file.setFileName(newFileName);
        file.setUpdateTime(new Date());
        if (!file.getIsDir()) {
            file.setExt(com.rabbit.pan.util.FileTypeUtil.getExt(newFileName));
            file.setFileType(com.rabbit.pan.util.FileTypeUtil.getCategory(newFileName));
        }
        fileInfoMapper.updateById(file);
    }

    /** 批量移动 */
    @Transactional(rollbackFor = Exception.class)
    public void move(Long userId, MoveDTO dto) {
        Long targetParentId = dto.getTargetParentId();
        validateParent(userId, targetParentId);
        List<Long> movingIds = dto.getFileIds();
        // 目标目录不能是移动对象自身或其后代
        for (Long id : movingIds) {
            if (id.equals(targetParentId)) {
                throw new BizException(ResultCode.OPERATION_NOT_ALLOWED, "目标目录不能是移动对象自身");
            }
            FileInfo target = targetParentId == 0 ? null : getOwned(userId, targetParentId);
            FileInfo file = getOwned(userId, id);
            if (file.getIsDir() && target != null && isDescendant(file.getId(), target)) {
                throw new BizException(ResultCode.OPERATION_NOT_ALLOWED, "不能将文件夹移动到其子目录内");
            }
        }
        Date now = new Date();
        for (Long id : movingIds) {
            FileInfo file = getOwned(userId, id);
            if (file.getParentId().equals(targetParentId)) {
                continue;
            }
            checkSiblingNameConflict(userId, targetParentId, file.getFileName(), id);
            file.setParentId(targetParentId);
            file.setUpdateTime(now);
            fileInfoMapper.updateById(file);
        }
        log.info("批量移动完成: userId={}, count={}, target={}", userId, movingIds.size(), targetParentId);
    }

    /** 批量复制（文件夹递归复制，文件复用同一存储对象引用） */
    @Transactional(rollbackFor = Exception.class)
    public int copy(Long userId, MoveDTO dto) {
        Long targetParentId = dto.getTargetParentId();
        validateParent(userId, targetParentId);
        int count = 0;
        for (Long id : dto.getFileIds()) {
            FileInfo source = getOwned(userId, id);
            if (source.getId().equals(targetParentId)) {
                throw new BizException(ResultCode.OPERATION_NOT_ALLOWED, "不能复制到自身");
            }
            if (source.getIsDir() && targetParentId != 0 && isDescendant(source.getId(),
                    targetParentId == 0 ? null : getOwned(userId, targetParentId))) {
                throw new BizException(ResultCode.OPERATION_NOT_ALLOWED, "不能将文件夹复制到其子目录内");
            }
            count += copyRecursive(userId, source, targetParentId, 0);
        }
        log.info("批量复制完成: userId={}, copied={}", userId, count);
        return count;
    }

    private int copyRecursive(Long userId, FileInfo source, Long targetParentId, int depth) {
        if (depth > 20) {
            throw new BizException(ResultCode.OPERATION_NOT_ALLOWED, "目录嵌套过深，复制终止");
        }
        FileInfo copy = new FileInfo();
        copy.setUserId(userId);
        copy.setParentId(targetParentId);
        copy.setFileName(uniqueCopyName(userId, targetParentId, source.getFileName()));
        copy.setIsDir(source.getIsDir());
        copy.setSize(source.getSize());
        copy.setFileType(source.getFileType());
        copy.setExt(source.getExt());
        copy.setStoragePath(source.getStoragePath());
        copy.setMd5(source.getMd5());
        copy.setStatus(Constants.FILE_STATUS_NORMAL);
        copy.setCreateTime(new Date());
        copy.setUpdateTime(new Date());
        fileInfoMapper.insert(copy);
        int count = 1;
        if (source.getIsDir()) {
            List<FileInfo> children = fileInfoMapper.selectList(new LambdaQueryWrapper<FileInfo>()
                    .eq(FileInfo::getParentId, source.getId())
                    .eq(FileInfo::getStatus, Constants.FILE_STATUS_NORMAL));
            for (FileInfo child : children) {
                count += copyRecursive(userId, child, copy.getId(), depth + 1);
            }
        }
        return count;
    }

    /** 复制名称冲突时追加 “(副本)” / “(副本2)” */
    private String uniqueCopyName(Long userId, Long parentId, String originName) {
        String base = com.rabbit.pan.util.FileTypeUtil.getBaseName(originName);
        String ext = originName.contains(".")
                ? originName.substring(originName.lastIndexOf('.')) : "";
        String candidate = originName;
        int seq = 0;
        while (existsSiblingName(userId, parentId, candidate)) {
            seq++;
            candidate = base + "(副本" + (seq == 1 ? "" : seq) + ")" + ext;
        }
        return candidate;
    }

    /** 软删除（移入回收站）：递归整棵子树 */
    @Transactional(rollbackFor = Exception.class)
    public int softDelete(Long userId, List<Long> fileIds) {
        Date now = new Date();
        int count = 0;
        for (Long id : fileIds) {
            FileInfo file = getOwned(userId, id);
            count += deleteSubtree(file, now);
        }
        log.info("移入回收站: userId={}, count={}", userId, count);
        return count;
    }

    private int deleteSubtree(FileInfo file, Date deleteTime) {
        int count = 0;
        file.setStatus(Constants.FILE_STATUS_RECYCLE);
        file.setOriginalParentId(file.getParentId());
        file.setDeleteTime(deleteTime);
        file.setUpdateTime(deleteTime);
        fileInfoMapper.updateById(file);
        count++;
        if (file.getIsDir()) {
            List<FileInfo> children = fileInfoMapper.selectList(new LambdaQueryWrapper<FileInfo>()
                    .eq(FileInfo::getParentId, file.getId())
                    .eq(FileInfo::getStatus, Constants.FILE_STATUS_NORMAL));
            for (FileInfo child : children) {
                count += deleteSubtree(child, deleteTime);
            }
        }
        return count;
    }

    // ==================== 公共校验 ====================

    /** 获取用户自己的文件（强制所属权校验，防水平越权） */
    public FileInfo getOwned(Long userId, Long fileId) {
        FileInfo file = fileInfoMapper.selectById(fileId);
        if (file == null || file.getUserId() == null || !file.getUserId().equals(userId)) {
            throw new BizException(ResultCode.FILE_NOT_FOUND);
        }
        return file;
    }

    /** 校验目标目录存在且为文件夹（0 表示根目录） */
    public void validateParent(Long userId, Long parentId) {
        if (parentId == null || parentId == 0) {
            return;
        }
        FileInfo parent = getOwned(userId, parentId);
        if (!Boolean.TRUE.equals(parent.getIsDir())
                || parent.getStatus() != Constants.FILE_STATUS_NORMAL) {
            throw new BizException(ResultCode.OPERATION_NOT_ALLOWED, "目标位置不是有效文件夹");
        }
    }

    /** 同级目录重名校验 */
    public void checkSiblingNameConflict(Long userId, Long parentId, String fileName, Long excludeId) {
        if (existsSiblingName(userId, parentId, fileName, excludeId)) {
            throw new BizException(ResultCode.NAME_CONFLICT);
        }
    }

    private boolean existsSiblingName(Long userId, Long parentId, String fileName) {
        return existsSiblingName(userId, parentId, fileName, null);
    }

    private boolean existsSiblingName(Long userId, Long parentId, String fileName, Long excludeId) {
        LambdaQueryWrapper<FileInfo> wrapper = new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getUserId, userId)
                .eq(FileInfo::getParentId, parentId == null ? 0L : parentId)
                .eq(FileInfo::getStatus, Constants.FILE_STATUS_NORMAL)
                .eq(FileInfo::getFileName, fileName);
        if (excludeId != null) {
            wrapper.ne(FileInfo::getId, excludeId);
        }
        return fileInfoMapper.selectCount(wrapper) > 0;
    }

    /** 判断 target 是否位于 root 子树内（含自身） */
    public boolean isDescendant(Long rootId, FileInfo target) {
        Long current = target.getParentId();
        int depth = 0;
        while (current != null && current != 0 && depth < 50) {
            if (current.equals(rootId)) {
                return true;
            }
            FileInfo parent = fileInfoMapper.selectById(current);
            current = parent == null ? null : parent.getParentId();
            depth++;
        }
        return false;
    }

    /** 收集文件夹子树下所有文件行（不含目录自身），用于下载/打包/物理删除 */
    public List<FileInfo> collectFiles(FileInfo root) {
        List<FileInfo> files = new ArrayList<>();
        Deque<FileInfo> stack = new ArrayDeque<>();
        stack.push(root);
        int guard = 0;
        while (!stack.isEmpty() && guard++ < 100_000) {
            FileInfo node = stack.pop();
            if (node.getIsDir()) {
                List<FileInfo> children = fileInfoMapper.selectList(new LambdaQueryWrapper<FileInfo>()
                        .eq(FileInfo::getParentId, node.getId())
                        .eq(FileInfo::getStatus, node.getStatus()));
                children.forEach(stack::push);
            } else {
                files.add(node);
            }
        }
        return files;
    }

    /** 校验 fileId 是否属于 root 子树（分享文件夹浏览安全校验） */
    public boolean inSubtree(Long rootId, Long targetId) {
        if (rootId.equals(targetId)) {
            return true;
        }
        FileInfo target = fileInfoMapper.selectById(targetId);
        if (target == null) {
            return false;
        }
        return isDescendant(rootId, target);
    }
}
