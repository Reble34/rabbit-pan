package com.rabbit.pan.controller;

import com.rabbit.pan.common.BizException;
import com.rabbit.pan.common.Result;
import com.rabbit.pan.common.ResultCode;
import com.rabbit.pan.entity.FileInfo;
import com.rabbit.pan.security.SecurityUtils;
import com.rabbit.pan.service.FileService;
import com.rabbit.pan.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 文件预览接口：图片 / 音视频 / 文档 / 压缩包
 */
@Tag(name = "文件预览")
@RestController
@RequestMapping("/api/v1/preview")
@RequiredArgsConstructor
public class PreviewController {

    private final FileService fileService;
    private final TransferService transferService;

    @Operation(summary = "图片预览（支持 ?thumb=1 缩略图）")
    @GetMapping("/image/{id}")
    public void image(@PathVariable Long id,
                      @RequestParam(defaultValue = "0") boolean thumb,
                      HttpServletResponse response) {
        FileInfo file = fileService.getOwned(SecurityUtils.currentUserId(), id);
        requireCategory(file, com.rabbit.pan.util.FileTypeUtil.TYPE_IMAGE);
        transferService.previewImage(file, thumb, response);
    }

    @Operation(summary = "音视频播放（支持 Range 拖动）")
    @GetMapping("/video/{id}")
    public void video(@PathVariable Long id, HttpServletRequest request, HttpServletResponse response) {
        FileInfo file = fileService.getOwned(SecurityUtils.currentUserId(), id);
        if (!com.rabbit.pan.util.FileTypeUtil.TYPE_VIDEO.equals(file.getFileType())
                && !com.rabbit.pan.util.FileTypeUtil.TYPE_AUDIO.equals(file.getFileType())) {
            throw new BizException(ResultCode.OPERATION_NOT_ALLOWED, "该文件不是音视频类型");
        }
        transferService.download(file, request.getHeader("Range"), response, true, null);
    }

    @Operation(summary = "文档预览（PDF/TXT/MD 直返；Office 暂不支持在线预览）")
    @GetMapping("/doc/{id}")
    public void doc(@PathVariable Long id, HttpServletResponse response) {
        FileInfo file = fileService.getOwned(SecurityUtils.currentUserId(), id);
        String type = file.getFileType();
        if (!List.of(com.rabbit.pan.util.FileTypeUtil.TYPE_PDF,
                com.rabbit.pan.util.FileTypeUtil.TYPE_TXT,
                com.rabbit.pan.util.FileTypeUtil.TYPE_MD).contains(type)) {
            throw new BizException(ResultCode.OPERATION_NOT_ALLOWED,
                    "该格式暂不支持在线预览，请下载后查看");
        }
        transferService.download(file, null, response, true, null);
    }

    @Operation(summary = "压缩包目录树（ZIP 在线查看）")
    @GetMapping("/zip/{id}")
    public Result<List<Map<String, Object>>> zip(@PathVariable Long id) {
        FileInfo file = fileService.getOwned(SecurityUtils.currentUserId(), id);
        requireCategory(file, com.rabbit.pan.util.FileTypeUtil.TYPE_ZIP);
        return Result.ok(transferService.zipEntries(file));
    }

    @Operation(summary = "压缩包单文件提取（不解压整个包）")
    @GetMapping("/zip/extract")
    public void zipExtract(@RequestParam Long fileId, @RequestParam String entry,
                           HttpServletResponse response) {
        FileInfo file = fileService.getOwned(SecurityUtils.currentUserId(), fileId);
        transferService.extractZipEntry(file, entry, response);
    }

    private void requireCategory(FileInfo file, String category) {
        if (!category.equals(file.getFileType())) {
            throw new BizException(ResultCode.OPERATION_NOT_ALLOWED,
                    "该文件不是" + category + "类型");
        }
    }
}
