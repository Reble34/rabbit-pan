package com.rabbit.pan.controller;

import com.rabbit.pan.common.Result;
import com.rabbit.pan.config.AppProperties;
import com.rabbit.pan.dto.VerifyPasswordDTO;
import com.rabbit.pan.entity.FileInfo;
import com.rabbit.pan.entity.FileShare;
import com.rabbit.pan.service.FileService;
import com.rabbit.pan.service.ShareService;
import com.rabbit.pan.service.TransferService;
import com.rabbit.pan.vo.FileVO;
import com.rabbit.pan.vo.ShareInfoVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 分享公开访问接口（无需登录，短码 + 临时凭证保护）
 */
@Tag(name = "分享公开访问")
@RestController
@RequiredArgsConstructor
public class PublicShareController {

    private final ShareService shareService;
    private final FileService fileService;
    private final TransferService transferService;
    private final AppProperties appProperties;

    @Operation(summary = "短链跳转入口：校验后 302 重定向到前端分享页")
    @GetMapping("/api/v1/s/{shortCode}")
    public void redirect(@PathVariable String shortCode, HttpServletResponse response) throws IOException {
        // 统一 302 到前端分享页，由前端展示状态（需提取码/已过期/内容预览）
        String base = appProperties.getFrontendBaseUrl() == null ? "" : appProperties.getFrontendBaseUrl();
        response.sendRedirect(base + "/share/" + shortCode);
    }

    @Operation(summary = "分享基本信息（返回文件名/大小/是否要提取码/过期时间）")
    @GetMapping("/api/v1/share/{shortCode}/info")
    public Result<ShareInfoVO> info(@PathVariable String shortCode) {
        return Result.ok(shareService.info(shortCode));
    }

    @Operation(summary = "校验提取码（通过后返回临时访问凭证）")
    @PostMapping("/api/v1/share/{shortCode}/verify")
    public Result<Map<String, String>> verify(@PathVariable String shortCode,
                                              @Valid @RequestBody VerifyPasswordDTO dto) {
        String token = shareService.verify(shortCode, dto.getPassword());
        return Result.ok(Map.of("shareToken", token));
    }

    @Operation(summary = "分享文件列表（带临时凭证浏览文件夹）")
    @GetMapping("/api/v1/share/{shortCode}/list")
    public Result<List<FileVO>> list(@PathVariable String shortCode,
                                     @RequestParam String shareToken,
                                     @RequestParam(defaultValue = "0") Long dirId) {
        FileShare share = shareService.resolveByTempToken(shareToken);
        // 短码与凭证必须匹配同一分享
        if (!share.getShortCode().equals(shortCode)) {
            throw new com.rabbit.pan.common.BizException(com.rabbit.pan.common.ResultCode.FORBIDDEN);
        }
        return Result.ok(shareService.listSharedFolder(share, dirId));
    }

    @Operation(summary = "分享文件下载（带临时凭证；仅预览权限时拒绝）")
    @GetMapping("/api/v1/share/{shortCode}/download/{fileId}")
    public void download(@PathVariable String shortCode, @PathVariable Long fileId,
                         @RequestParam String shareToken, HttpServletRequest request,
                         HttpServletResponse response) {
        FileShare share = shareService.resolveByTempToken(shareToken);
        if (!share.getShortCode().equals(shortCode)) {
            throw new com.rabbit.pan.common.BizException(com.rabbit.pan.common.ResultCode.FORBIDDEN);
        }
        if (share.getCanDownload() == null || share.getCanDownload() != 1) {
            throw new com.rabbit.pan.common.BizException(
                    com.rabbit.pan.common.ResultCode.OPERATION_NOT_ALLOWED, "该分享仅支持在线预览");
        }
        FileInfo file = shareService.getSharedFile(share, fileId);
        transferService.download(file, request.getHeader("Range"), response, false, null);
    }

    @Operation(summary = "分享文件在线预览（带临时凭证）")
    @GetMapping("/api/v1/share/{shortCode}/preview/{fileId}")
    public void preview(@PathVariable String shortCode, @PathVariable Long fileId,
                        @RequestParam String shareToken,
                        @RequestParam(defaultValue = "0") boolean thumb,
                        HttpServletRequest request, HttpServletResponse response) {
        FileShare share = shareService.resolveByTempToken(shareToken);
        if (!share.getShortCode().equals(shortCode)) {
            throw new com.rabbit.pan.common.BizException(com.rabbit.pan.common.ResultCode.FORBIDDEN);
        }
        FileInfo file = shareService.getSharedFile(share, fileId);
        String type = file.getFileType();
        switch (type == null ? "" : type) {
            case com.rabbit.pan.util.FileTypeUtil.TYPE_IMAGE ->
                    transferService.previewImage(file, thumb, response);
            case com.rabbit.pan.util.FileTypeUtil.TYPE_VIDEO,
                 com.rabbit.pan.util.FileTypeUtil.TYPE_AUDIO ->
                    transferService.download(file, request.getHeader("Range"), response, true, null);
            case com.rabbit.pan.util.FileTypeUtil.TYPE_PDF,
                 com.rabbit.pan.util.FileTypeUtil.TYPE_TXT,
                 com.rabbit.pan.util.FileTypeUtil.TYPE_MD ->
                    transferService.download(file, null, response, true, null);
            default -> throw new com.rabbit.pan.common.BizException(
                    com.rabbit.pan.common.ResultCode.OPERATION_NOT_ALLOWED,
                    "该格式暂不支持在线预览，" + (share.getCanDownload() == 1 ? "可下载后查看" : "且该分享不允许下载"));
        }
    }
}
