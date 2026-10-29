package com.rabbit.pan.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbit.pan.common.BizException;
import com.rabbit.pan.common.Constants;
import com.rabbit.pan.common.ResultCode;
import com.rabbit.pan.entity.FileInfo;
import com.rabbit.pan.mapper.FileInfoMapper;
import com.rabbit.pan.util.FileTypeUtil;
import com.rabbit.pan.util.MinioTemplate;
import com.rabbit.pan.vo.ZipTaskVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 文件传输服务：流式下载（Range 断点续传）/ 图片缩略图 / 音视频拖动 / 文档与压缩包预览 / 批量打包 ZIP
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransferService {

    private static final Pattern RANGE_PATTERN = Pattern.compile("bytes=(\\d*)-(\\d*)");
    private static final int BUFFER_SIZE = 64 * 1024;
    private static final long ZIP_PART_SIZE = 10 * 1024 * 1024L;

    private final FileInfoMapper fileInfoMapper;
    private final FileService fileService;
    private final MinioTemplate minioTemplate;
    private final RedisService redisService;
    private final ObjectMapper objectMapper;

    // ==================== 下载 ====================

    /** 单文件流式下载，支持 Range 请求（断点续传/播放器拖动） */
    public void download(FileInfo file, String rangeHeader, HttpServletResponse response,
                         boolean inline, String downloadName) {
        if (file.getStoragePath() == null) {
            throw new BizException(ResultCode.FILE_NOT_FOUND);
        }
        // SVG 属文本型矢量格式，内嵌脚本在文档上下文执行：一律按附件下发，禁止 inline
        if (inline && "svg".equals(file.getExt())) {
            inline = false;
        }
        long objectSize;
        try {
            objectSize = minioTemplate.statSize(file.getStoragePath());
        } catch (Exception e) {
            log.error("存储对象读取失败: {}", file.getStoragePath(), e);
            throw new BizException(ResultCode.SERVER_ERROR, "文件存储读取失败");
        }
        String fileName = downloadName == null ? file.getFileName() : downloadName;
        String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Accept-Ranges", "bytes");
        response.setHeader("Content-Disposition", (inline ? "inline" : "attachment")
                + "; filename*=UTF-8''" + encodedName);
        response.setContentType(file.getIsDir() ? "application/octet-stream"
                : FileTypeUtil.getMimeType(fileName));
        applyMediaSecurityHeaders(response);

        try {
            if (rangeHeader != null && rangeHeader.matches("bytes=\\d*-\\d*")) {
                long[] range = parseRange(rangeHeader, objectSize);
                long start = range[0];
                long end = range[1];
                long length = end - start + 1;
                response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
                response.setHeader("Content-Range", "bytes " + start + "-" + end + "/" + objectSize);
                response.setContentLengthLong(length);
                try (InputStream in = minioTemplate.getRange(file.getStoragePath(), start, length)) {
                    in.transferTo(response.getOutputStream());
                }
            } else {
                response.setContentLengthLong(objectSize);
                try (InputStream in = minioTemplate.get(file.getStoragePath())) {
                    in.transferTo(response.getOutputStream());
                }
            }
            response.flushBuffer();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            // 客户端中断连接属于正常现象
            log.debug("下载流中断: {}", e.getMessage());
        }
    }

    private long[] parseRange(String header, long total) {
        Matcher matcher = RANGE_PATTERN.matcher(header);
        if (!matcher.matches()) {
            throw new BizException(ResultCode.PARAM_ERROR, "Range 头不合法");
        }
        String startStr = matcher.group(1);
        String endStr = matcher.group(2);
        long start;
        long end;
        if (startStr.isEmpty()) {
            // suffix range: bytes=-500
            long suffix = Long.parseLong(endStr);
            start = Math.max(0, total - suffix);
            end = total - 1;
        } else {
            start = Long.parseLong(startStr);
            end = endStr.isEmpty() ? total - 1 : Math.min(Long.parseLong(endStr), total - 1);
        }
        if (start > end || start >= total) {
            throw new BizException(ResultCode.PARAM_ERROR, "Range 范围不合法");
        }
        return new long[]{start, end};
    }

    // ==================== 预览 ====================

    /** 图片预览（支持缩略图：首次生成后缓存到 MinIO thumbs/） */
    public void previewImage(FileInfo file, boolean thumb, HttpServletResponse response) {
        // SVG 可内嵌脚本，同源文档上下文执行即存储型 XSS：禁止在线预览，仅允许附件下载
        if ("svg".equals(file.getExt())) {
            throw new BizException(ResultCode.OPERATION_NOT_ALLOWED, "SVG 文件不支持在线预览，请下载后查看");
        }
        if (thumb) {
            String thumbKey = Constants.MINIO_DIR_THUMB + file.getId() + ".jpg";
            if (minioTemplate.exists(thumbKey)) {
                streamObject(thumbKey, "image/jpeg", response);
                return;
            }
            if (generateThumbnail(file, thumbKey)) {
                streamObject(thumbKey, "image/jpeg", response);
                return;
            }
        }
        streamObject(file.getStoragePath(), FileTypeUtil.getMimeType(file.getFileName()), response);
    }

    private boolean generateThumbnail(FileInfo file, String thumbKey) {
        String ext = file.getExt() == null ? "" : file.getExt();
        // ImageIO 原生不支持 webp，直接回退原图
        if (!List.of("jpg", "jpeg", "png", "gif", "bmp").contains(ext)) {
            return false;
        }
        try (InputStream in = minioTemplate.get(file.getStoragePath())) {
            BufferedImage source = ImageIO.read(in);
            if (source == null) {
                return false;
            }
            int targetWidth = 200;
            int targetHeight = Math.max(1, (int) Math.round(source.getHeight() * 200.0 / source.getWidth()));
            BufferedImage scaled = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(source, 0, 0, targetWidth, targetHeight, null);
            g.dispose();
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            ImageIO.write(scaled, "jpg", bos);
            minioTemplate.put(thumbKey, new java.io.ByteArrayInputStream(bos.toByteArray()),
                    bos.size(), "image/jpeg");
            return true;
        } catch (Exception e) {
            log.warn("缩略图生成失败: fileId={}", file.getId(), e);
            return false;
        }
    }

    /** 媒体响应统一安全头：禁止 MIME 嗅探 + 禁止脚本（防 SVG/伪装内容在文档上下文执行） */
    private void applyMediaSecurityHeaders(HttpServletResponse response) {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Security-Policy", "default-src 'none'; sandbox");
    }

    private void streamObject(String objectKey, String contentType, HttpServletResponse response) {
        try {
            response.setContentType(contentType);
            applyMediaSecurityHeaders(response);
            try (InputStream in = minioTemplate.get(objectKey)) {
                in.transferTo(response.getOutputStream());
            }
            response.flushBuffer();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.debug("预览流中断: {}", e.getMessage());
        }
    }

    /** 压缩包目录树（ZIP 原生支持；RAR/7z 提示不支持） */
    public List<Map<String, Object>> zipEntries(FileInfo file) {
        if (!"zip".equals(file.getExt())) {
            throw new BizException(ResultCode.OPERATION_NOT_ALLOWED, "仅支持 ZIP 压缩包在线预览");
        }
        List<Map<String, Object>> entries = new ArrayList<>();
        try (InputStream in = minioTemplate.get(file.getStoragePath());
             ZipInputStream zis = new ZipInputStream(in, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                Map<String, Object> item = new HashMap<>();
                item.put("name", entry.getName());
                item.put("isDir", entry.isDirectory());
                item.put("size", entry.getSize());
                entries.add(item);
                if (entries.size() >= 5000) {
                    break;
                }
            }
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("压缩包读取失败: fileId={}", file.getId(), e);
            throw new BizException(ResultCode.SERVER_ERROR, "压缩包读取失败");
        }
        return entries;
    }

    /** 压缩包单文件提取（不解压整个包，顺序流式扫描目标条目） */
    public void extractZipEntry(FileInfo file, String entryName, HttpServletResponse response) {
        if (!"zip".equals(file.getExt())) {
            throw new BizException(ResultCode.OPERATION_NOT_ALLOWED, "仅支持 ZIP 压缩包文件提取");
        }
        try (InputStream in = minioTemplate.get(file.getStoragePath());
             ZipInputStream zis = new ZipInputStream(in, StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals(entryName) && !entry.isDirectory()) {
                    String encodedName = URLEncoder.encode(
                            entry.getName().substring(entry.getName().lastIndexOf('/') + 1),
                            StandardCharsets.UTF_8).replace("+", "%20");
                    response.setContentType("application/octet-stream");
                    response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedName);
                    zis.transferTo(response.getOutputStream());
                    response.flushBuffer();
                    return;
                }
            }
            throw new BizException(ResultCode.NOT_FOUND, "压缩包内未找到该文件");
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("压缩包提取失败: fileId={}, entry={}", file.getId(), entryName, e);
            throw new BizException(ResultCode.SERVER_ERROR, "压缩包提取失败");
        }
    }

    // ==================== 批量打包 ZIP（异步任务） ====================

    /** 创建打包任务：立即返回 taskId，后台异步打包 */
    public String createZipTask(Long userId, List<Long> fileIds) {
        String taskId = java.util.UUID.randomUUID().toString().replace("-", "");
        ZipTaskVO vo = new ZipTaskVO();
        vo.setStatus(0);
        vo.setOwnerUserId(userId);
        redisService.setObject(Constants.RedisKeys.ZIP_TASK + taskId + ":meta",
                Map.of("userId", String.valueOf(userId), "fileIds", fileIds),
                java.time.Duration.ofHours(24));
        redisService.setObject(Constants.RedisKeys.ZIP_TASK + taskId, vo,
                java.time.Duration.ofHours(24));
        return taskId;
    }

    /** 后台执行打包（zipExecutor 线程池） */
    @Async("zipExecutor")
    public void executeZipTask(String taskId) {
        Map<String, Object> meta = redisService.getObject(
                Constants.RedisKeys.ZIP_TASK + taskId + ":meta",
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {
                });
        ZipTaskVO vo = redisService.getObject(Constants.RedisKeys.ZIP_TASK + taskId, ZipTaskVO.class);
        if (meta == null || vo == null) {
            return;
        }
        Long userId = Long.valueOf((String) meta.get("userId"));
        @SuppressWarnings("unchecked")
        List<Number> rawIds = (List<Number>) meta.get("fileIds");
        try {
            String zipKey = Constants.MINIO_DIR_ZIP + taskId + ".zip";
            List<FileInfo> roots = new ArrayList<>();
            for (Number id : rawIds) {
                roots.add(fileService.getOwned(userId, id.longValue()));
            }
            // 输入管道：zip 写入端 -> 读取端上传 MinIO
            try (java.io.PipedInputStream pin = new java.io.PipedInputStream(1024 * 1024);
                 java.io.PipedOutputStream pout = new java.io.PipedOutputStream(pin)) {
                Thread uploadThread = new Thread(() -> {
                    try (InputStream in = pin) {
                        minioTemplate.putStreamed(zipKey, in, ZIP_PART_SIZE, "application/zip");
                    } catch (Exception e) {
                        log.error("ZIP 上传线程异常: taskId={}", taskId, e);
                    }
                }, "zip-upload-" + taskId);
                uploadThread.start();
                try (ZipOutputStream zos = new ZipOutputStream(pout, StandardCharsets.UTF_8)) {
                    for (FileInfo root : roots) {
                        if (root.getIsDir()) {
                            zipFolder(zos, root, root.getFileName() + "/");
                        } else {
                            zipFile(zos, root, dedupeEntryName(root.getFileName(), new java.util.HashSet<>()));
                        }
                    }
                }
                uploadThread.join(300_000);
            }
            vo.setStatus(1);
            vo.setUrl("/api/v1/download/zip/file/" + taskId);
            redisService.setObject(Constants.RedisKeys.ZIP_TASK + taskId, vo,
                    java.time.Duration.ofHours(24));
            log.info("ZIP 打包完成: taskId={}, userId={}", taskId, userId);
        } catch (Exception e) {
            log.error("ZIP 打包失败: taskId={}", taskId, e);
            vo.setStatus(2);
            redisService.setObject(Constants.RedisKeys.ZIP_TASK + taskId, vo,
                    java.time.Duration.ofHours(1));
        }
    }

    private final Map<String, Integer> entryNameCounter = new HashMap<>();

    private String dedupeEntryName(String name, java.util.Set<String> used) {
        String candidate = name;
        int seq = 0;
        while (used.contains(candidate)) {
            seq++;
            String base = FileTypeUtil.getBaseName(name);
            String ext = name.contains(".") ? name.substring(name.lastIndexOf('.')) : "";
            candidate = base + "(" + seq + ")" + ext;
        }
        used.add(candidate);
        return candidate;
    }

    private void zipFolder(ZipOutputStream zos, FileInfo folder, String prefix) throws Exception {
        List<FileInfo> children = fileInfoMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FileInfo>()
                        .eq(FileInfo::getParentId, folder.getId())
                        .eq(FileInfo::getStatus, Constants.FILE_STATUS_NORMAL));
        for (FileInfo child : children) {
            if (child.getIsDir()) {
                zipFolder(zos, child, prefix + child.getFileName() + "/");
            } else {
                zos.putNextEntry(new ZipEntry(prefix + child.getFileName()));
                try (InputStream in = minioTemplate.get(child.getStoragePath())) {
                    in.transferTo(zos);
                }
                zos.closeEntry();
            }
        }
    }

    private void zipFile(ZipOutputStream zos, FileInfo file, String entryName) throws Exception {
        zos.putNextEntry(new ZipEntry(entryName));
        try (InputStream in = minioTemplate.get(file.getStoragePath())) {
            in.transferTo(zos);
        }
        zos.closeEntry();
    }

    /** 查询打包任务状态（强制属主校验，防水平越权） */
    public ZipTaskVO zipStatus(Long userId, String taskId) {
        ZipTaskVO vo = redisService.getObject(Constants.RedisKeys.ZIP_TASK + taskId, ZipTaskVO.class);
        if (vo == null) {
            throw new BizException(ResultCode.NOT_FOUND, "打包任务不存在或已过期");
        }
        requireTaskOwner(userId, vo);
        return vo;
    }

    /** 校验 ZIP 任务属主；ownerUserId 缺失（历史任务）时拒绝下载 */
    private void requireTaskOwner(Long userId, ZipTaskVO vo) {
        if (vo.getOwnerUserId() == null || !vo.getOwnerUserId().equals(userId)) {
            throw new BizException(ResultCode.NOT_FOUND, "打包任务不存在或已过期");
        }
    }

    /** 下载打包完成的 ZIP（强制属主校验，防水平越权） */
    public void downloadZip(Long userId, String taskId, HttpServletResponse response) {
        ZipTaskVO vo = zipStatus(userId, taskId);
        if (vo.getStatus() != 1) {
            throw new BizException(ResultCode.OPERATION_NOT_ALLOWED, "打包尚未完成或已失败");
        }
        String zipKey = Constants.MINIO_DIR_ZIP + taskId + ".zip";
        response.setContentType("application/zip");
        String encodedName = URLEncoder.encode("Rabbit网盘打包_" + taskId.substring(0, 8) + ".zip",
                StandardCharsets.UTF_8).replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + encodedName);
        try (InputStream in = minioTemplate.get(zipKey)) {
            in.transferTo(response.getOutputStream());
            response.flushBuffer();
        } catch (Exception e) {
            log.debug("ZIP 下载中断: {}", e.getMessage());
        }
    }
}
