package com.rabbit.pan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.rabbit.pan.common.BizException;
import com.rabbit.pan.common.Constants;
import com.rabbit.pan.common.ResultCode;
import com.rabbit.pan.config.AppProperties;
import com.rabbit.pan.dto.UploadCheckDTO;
import com.rabbit.pan.dto.UploadMergeDTO;
import com.rabbit.pan.entity.FileInfo;
import com.rabbit.pan.entity.SysUser;
import com.rabbit.pan.mapper.FileInfoMapper;
import com.rabbit.pan.mapper.SysUserMapper;
import com.rabbit.pan.util.FileTypeUtil;
import com.rabbit.pan.util.MinioTemplate;
import com.rabbit.pan.vo.UploadInitVO;
import io.minio.ComposeSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.*;

/**
 * 上传服务：秒传校验 / 分片初始化 / 分片上传 / 合并落库 / 单文件直传
 *
 * <p>分片存储于 MinIO tmp/chunk/{uploadId}/{index}；合并使用 MinIO composeObject
 * 服务端拼接（要求除最后一片外每片 >= 5MB）；任务状态与已传分片记录在 Redis，
 * 支持断点续传（24 小时内有效）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UploadService {

    private final FileInfoMapper fileInfoMapper;
    private final SysUserMapper sysUserMapper;
    private final FileService fileService;
    private final StorageService storageService;
    private final ConfigService configService;
    private final MinioTemplate minioTemplate;
    private final RedisService redisService;
    private final AppProperties appProperties;

    /** 秒传校验：全局 MD5 命中则直接创建文件引用 */
    public Map<String, Object> check(Long userId, UploadCheckDTO dto) {
        validateUpload(dto.getFileName(), dto.getFileSize());
        FileInfo hit = fileInfoMapper.selectOne(new LambdaQueryWrapper<FileInfo>()
                .eq(FileInfo::getMd5, dto.getMd5())
                .eq(FileInfo::getIsDir, false)
                .eq(FileInfo::getStatus, Constants.FILE_STATUS_NORMAL)
                .last("LIMIT 1"));
        Map<String, Object> result = new HashMap<>();
        if (hit == null || hit.getStoragePath() == null) {
            result.put("finished", false);
            return result;
        }
        // 配额校验后创建新文件行（引用同一存储对象，秒传）
        SysUser user = sysUserMapper.selectById(userId);
        storageService.checkQuota(userId, user.getStorageTotal(), dto.getFileSize());
        Long fileId = insertFileRow(userId, dto.getParentDirId(), dto.getFileName(),
                dto.getFileSize(), hit.getStoragePath(), dto.getMd5());
        result.put("finished", true);
        result.put("fileId", fileId);
        log.info("秒传命中: userId={}, md5={}, fileId={}", userId, dto.getMd5(), fileId);
        return result;
    }

    /** 初始化分片任务（支持断点续传：重复 init 返回已传分片） */
    public UploadInitVO init(Long userId, UploadCheckDTO dto) {
        validateUpload(dto.getFileName(), dto.getFileSize());
        String uploadId = UUID.randomUUID().toString().replace("-", "");
        int chunkSize = appProperties.getUpload().getChunkSizeMb() * 1024 * 1024;
        int chunkCount = (int) Math.ceil(dto.getFileSize() * 1.0 / chunkSize);
        Map<String, String> meta = new HashMap<>();
        meta.put("userId", String.valueOf(userId));
        meta.put("fileName", dto.getFileName());
        meta.put("fileSize", String.valueOf(dto.getFileSize()));
        meta.put("md5", dto.getMd5());
        meta.put("parentDirId", String.valueOf(dto.getParentDirId()));
        meta.put("chunkCount", String.valueOf(chunkCount));
        meta.put("chunkSize", String.valueOf(chunkSize));
        redisService.putAllHash(Constants.RedisKeys.UPLOAD_TASK + uploadId, meta,
                java.time.Duration.ofHours(appProperties.getUpload().getChunkExpireHours()));
        // 断点续传：若同一文件此前已初始化（按 md5 匹配），沿用已传分片 —— 简化实现：新任务从 0 开始，
        // 前端断点续传通过携带原 uploadId 重新调用 init 完成本接口的幂等续传。
        UploadInitVO vo = new UploadInitVO();
        vo.setUploadId(uploadId);
        vo.setChunkSize(chunkSize);
        vo.setUploadedChunks(List.of());
        log.info("初始化分片任务: userId={}, uploadId={}, chunks={}", userId, uploadId, chunkCount);
        return vo;
    }

    /** 断点续传专用：携带既有 uploadId 重新 init，返回已传分片列表 */
    public UploadInitVO resume(Long userId, String uploadId, UploadCheckDTO dto) {
        Map<Object, Object> meta = redisService.getHash(Constants.RedisKeys.UPLOAD_TASK + uploadId);
        if (meta.isEmpty()) {
            // 任务过期：重新初始化
            return init(userId, dto);
        }
        requireOwner(userId, meta);
        Set<String> members = redisService.sMembers(Constants.RedisKeys.UPLOAD_CHUNKS + uploadId);
        List<Integer> uploaded = new ArrayList<>();
        if (members != null) {
            members.forEach(m -> uploaded.add(Integer.parseInt(m)));
        }
        Collections.sort(uploaded);
        UploadInitVO vo = new UploadInitVO();
        vo.setUploadId(uploadId);
        vo.setChunkSize(Integer.parseInt((String) meta.get("chunkSize")));
        vo.setUploadedChunks(uploaded);
        return vo;
    }

    /** 保存单个分片到 MinIO 临时目录 */
    public void saveChunk(Long userId, String uploadId, Integer chunkIndex, MultipartFile file) {
        Map<Object, Object> meta = requireTask(userId, uploadId);
        if (chunkIndex == null || chunkIndex < 0) {
            throw new BizException(ResultCode.PARAM_ERROR, "分片序号不合法");
        }
        int chunkCount = Integer.parseInt((String) meta.get("chunkCount"));
        if (chunkIndex >= chunkCount) {
            throw new BizException(ResultCode.PARAM_ERROR, "分片序号超出总数");
        }
        String objectKey = Constants.MINIO_DIR_CHUNK + uploadId + "/" + chunkIndex;
        try (InputStream in = file.getInputStream()) {
            minioTemplate.put(objectKey, in, file.getSize(), "application/octet-stream");
        } catch (Exception e) {
            log.error("分片保存失败: uploadId={}, index={}", uploadId, chunkIndex, e);
            throw new BizException(ResultCode.SERVER_ERROR, "分片保存失败");
        }
        redisService.sAdd(Constants.RedisKeys.UPLOAD_CHUNKS + uploadId, String.valueOf(chunkIndex),
                java.time.Duration.ofHours(appProperties.getUpload().getChunkExpireHours()));
    }

    /** 合并分片：校验完整性 → MinIO compose 拼接 → 落库 → 清理临时资源 */
    public Long merge(Long userId, UploadMergeDTO dto) {
        Map<Object, Object> meta = requireTask(userId, dto.getUploadId());
        int chunkCount = Integer.parseInt((String) meta.get("chunkCount"));
        Set<String> uploaded = redisService.sMembers(Constants.RedisKeys.UPLOAD_CHUNKS + dto.getUploadId());
        if (uploaded == null || uploaded.size() < chunkCount) {
            throw new BizException(ResultCode.UPLOAD_TASK_INVALID,
                    "分片不完整（" + (uploaded == null ? 0 : uploaded.size()) + "/" + chunkCount + "），请补传后重试");
        }
        // 配额与合法性的最终校验
        SysUser user = sysUserMapper.selectById(userId);
        long fileSize = dto.getFileSize();
        validateUpload(dto.getFileName(), fileSize);
        storageService.checkQuota(userId, user.getStorageTotal(), fileSize);

        String ext = FileTypeUtil.getExt(dto.getFileName());
        String objectKey = Constants.MINIO_DIR_FILES + userId + "/"
                + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8)
                + (ext.isEmpty() ? "" : "." + ext);
        try {
            if (chunkCount == 1) {
                minioTemplate.copy(Constants.MINIO_DIR_CHUNK + dto.getUploadId() + "/0", objectKey);
            } else {
                List<ComposeSource> sources = new ArrayList<>();
                for (int i = 0; i < chunkCount; i++) {
                    sources.add(ComposeSource.builder()
                            .bucket(minioTemplate.bucket())
                            .object(Constants.MINIO_DIR_CHUNK + dto.getUploadId() + "/" + i)
                            .build());
                }
                minioTemplate.compose(sources, objectKey);
            }
            long actualSize = minioTemplate.statSize(objectKey);
            if (Math.abs(actualSize - fileSize) > chunkCount * 1024L) {
                minioTemplate.remove(objectKey);
                throw new BizException(ResultCode.SERVER_ERROR, "合并后文件大小校验失败");
            }
            // 魔数校验：与直传一致，拦截伪装扩展名的 PE 可执行内容（分片通道同样不可绕过）
            try (InputStream headIn = minioTemplate.get(objectKey)) {
                byte[] head = new byte[2];
                int n = headIn.read(head);
                if (n == 2 && FileTypeUtil.isExecutableMagic(head)) {
                    minioTemplate.remove(objectKey);
                    throw new BizException(ResultCode.FILE_TYPE_BLOCKED, "文件内容为可执行程序，已拦截");
                }
            }
        } catch (BizException e) {
            // 拦截/校验失败时兜底清理已合并对象，防止绕过配额的存储残留
            try {
                minioTemplate.remove(objectKey);
            } catch (Exception ignore) {
            }
            throw e;
        } catch (Exception e) {
            log.error("分片合并失败: uploadId={}", dto.getUploadId(), e);
            try {
                minioTemplate.remove(objectKey);
            } catch (Exception ignore) {
            }
            throw new BizException(ResultCode.SERVER_ERROR, "分片合并失败");
        }
        Long fileId = insertFileRow(userId, dto.getParentDirId(), dto.getFileName(), fileSize,
                objectKey, dto.getMd5());
        // 清理临时分片与 Redis 任务状态
        cleanupTask(dto.getUploadId(), chunkCount);
        log.info("分片合并完成: userId={}, uploadId={}, fileId={}, size={}", userId, dto.getUploadId(), fileId, fileSize);
        return fileId;
    }

    /** 取消上传：清理临时分片与任务状态 */
    public void cancel(Long userId, String uploadId) {
        Map<Object, Object> meta = redisService.getHash(Constants.RedisKeys.UPLOAD_TASK + uploadId);
        if (meta.isEmpty()) {
            return;
        }
        requireOwner(userId, meta);
        int chunkCount = Integer.parseInt((String) meta.getOrDefault("chunkCount", "0"));
        cleanupTask(uploadId, chunkCount);
        log.info("上传任务已取消: userId={}, uploadId={}", userId, uploadId);
    }

    /** 小文件单请求直传 */
    public Long singleUpload(Long userId, Long parentDirId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ResultCode.PARAM_ERROR, "文件不能为空");
        }
        String fileName = file.getOriginalFilename() == null ? "unnamed" : file.getOriginalFilename();
        validateUpload(fileName, file.getSize());
        SysUser user = sysUserMapper.selectById(userId);
        storageService.checkQuota(userId, user.getStorageTotal(), file.getSize());
        // 魔数校验：拦截伪装为普通文件的 PE 可执行内容
        try (InputStream headStream = file.getInputStream()) {
            byte[] head = new byte[2];
            int n = headStream.read(head);
            if (n == 2 && FileTypeUtil.isExecutableMagic(head)) {
                throw new BizException(ResultCode.FILE_TYPE_BLOCKED);
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception ignored) {
        }
        String ext = FileTypeUtil.getExt(fileName);
        String objectKey = Constants.MINIO_DIR_FILES + userId + "/"
                + System.currentTimeMillis() + "_" + UUID.randomUUID().toString().substring(0, 8)
                + (ext.isEmpty() ? "" : "." + ext);
        try (InputStream in = file.getInputStream()) {
            minioTemplate.put(objectKey, in, file.getSize(), FileTypeUtil.getMimeType(ext));
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("文件上传失败: userId={}", userId, e);
            throw new BizException(ResultCode.SERVER_ERROR, "文件上传失败");
        }
        String md5 = null;
        try (InputStream in = file.getInputStream()) {
            md5 = org.springframework.util.DigestUtils.md5DigestAsHex(in);
        } catch (Exception ignored) {
        }
        return insertFileRow(userId, parentDirId, fileName, file.getSize(), objectKey, md5);
    }

    // ==================== 内部方法 ====================

    private void validateUpload(String fileName, long size) {
        if (FileTypeUtil.isForbiddenExt(fileName)) {
            throw new BizException(ResultCode.FILE_TYPE_BLOCKED,
                    "该文件类型（" + FileTypeUtil.getExt(fileName) + "）为高危类型，禁止上传");
        }
        String allowed = configService.get(ConfigService.KEY_ALLOWED_EXTS, "");
        String ext = FileTypeUtil.getExt(fileName);
        if (!allowed.isEmpty() && (ext.isEmpty() || !java.util.Arrays.asList(allowed.split(",")).contains(ext))) {
            throw new BizException(ResultCode.FILE_TYPE_BLOCKED,
                    "不支持的文件类型: " + (ext.isEmpty() ? "(无扩展名)" : ext));
        }
        long maxBytes = configService.getInt(ConfigService.KEY_MAX_FILE_MB, 2048) * 1024L * 1024;
        if (size > maxBytes) {
            throw new BizException(ResultCode.FILE_TOO_LARGE,
                    "文件超出单文件大小上限 " + configService.getInt(ConfigService.KEY_MAX_FILE_MB, 2048) + "MB");
        }
    }

    private Long insertFileRow(Long userId, Long parentDirId, String fileName, long size,
                               String objectKey, String md5) {
        fileService.validateParent(userId, parentDirId);
        String finalName = uniqueName(userId, parentDirId, fileName);
        FileInfo file = new FileInfo();
        file.setUserId(userId);
        file.setParentId(parentDirId == null ? 0L : parentDirId);
        file.setFileName(finalName);
        file.setIsDir(false);
        file.setSize(size);
        file.setFileType(FileTypeUtil.getCategory(fileName));
        file.setExt(FileTypeUtil.getExt(fileName));
        file.setStoragePath(objectKey);
        file.setMd5(md5);
        file.setStatus(Constants.FILE_STATUS_NORMAL);
        file.setCreateTime(new Date());
        file.setUpdateTime(new Date());
        fileInfoMapper.insert(file);
        return file.getId();
    }

    private String uniqueName(Long userId, Long parentId, String fileName) {
        String base = FileTypeUtil.getBaseName(fileName);
        String ext = fileName.contains(".") ? fileName.substring(fileName.lastIndexOf('.')) : "";
        String candidate = fileName;
        int seq = 0;
        while (true) {
            LambdaQueryWrapper<FileInfo> wrapper = new LambdaQueryWrapper<FileInfo>()
                    .eq(FileInfo::getUserId, userId)
                    .eq(FileInfo::getParentId, parentId == null ? 0L : parentId)
                    .eq(FileInfo::getStatus, Constants.FILE_STATUS_NORMAL)
                    .eq(FileInfo::getFileName, candidate);
            if (fileInfoMapper.selectCount(wrapper) == 0) {
                return candidate;
            }
            seq++;
            candidate = base + "(" + seq + ")" + ext;
        }
    }

    private Map<Object, Object> requireTask(Long userId, String uploadId) {
        Map<Object, Object> meta = redisService.getHash(Constants.RedisKeys.UPLOAD_TASK + uploadId);
        if (meta.isEmpty()) {
            throw new BizException(ResultCode.UPLOAD_TASK_INVALID);
        }
        requireOwner(userId, meta);
        return meta;
    }

    private void requireOwner(Long userId, Map<Object, Object> meta) {
        if (!String.valueOf(userId).equals(meta.get("userId"))) {
            throw new BizException(ResultCode.FORBIDDEN);
        }
    }

    private void cleanupTask(String uploadId, int chunkCount) {
        List<String> keys = new ArrayList<>();
        for (int i = 0; i < chunkCount; i++) {
            keys.add(Constants.MINIO_DIR_CHUNK + uploadId + "/" + i);
        }
        minioTemplate.removeBatch(keys);
        redisService.delete(Constants.RedisKeys.UPLOAD_TASK + uploadId);
        redisService.delete(Constants.RedisKeys.UPLOAD_CHUNKS + uploadId);
    }
}
