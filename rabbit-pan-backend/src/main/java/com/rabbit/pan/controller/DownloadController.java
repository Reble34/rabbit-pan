package com.rabbit.pan.controller;

import com.rabbit.pan.common.Result;
import com.rabbit.pan.entity.FileInfo;
import com.rabbit.pan.security.SecurityUtils;
import com.rabbit.pan.service.FileService;
import com.rabbit.pan.service.OpLogService;
import com.rabbit.pan.service.TransferService;
import com.rabbit.pan.vo.ZipTaskVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 下载接口：单文件流式下载（Range）/ 批量打包 ZIP（异步任务）
 */
@Tag(name = "下载")
@RestController
@RequestMapping("/api/v1/download")
@RequiredArgsConstructor
public class DownloadController {

    private final FileService fileService;
    private final TransferService transferService;
    private final OpLogService opLogService;

    @Operation(summary = "单文件下载（流式，支持 Range 断点续传）")
    @GetMapping("/{id}")
    public void download(@PathVariable Long id, HttpServletRequest request,
                         HttpServletResponse response) {
        FileInfo file = fileService.getOwned(SecurityUtils.currentUserId(), id);
        transferService.download(file, request.getHeader("Range"), response, false, null);
        opLogService.record(SecurityUtils.currentUserId(), "DOWNLOAD",
                "下载文件: " + file.getFileName(), null);
    }

    @Operation(summary = "批量打包下载（异步 ZIP，返回任务 ID）")
    @PostMapping("/batch")
    public Result<Map<String, String>> batchZip(@RequestBody List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty() || fileIds.size() > 1000) {
            throw new com.rabbit.pan.common.BizException(
                    com.rabbit.pan.common.ResultCode.PARAM_ERROR, "打包数量需在 1-1000 之间");
        }
        String taskId = transferService.createZipTask(SecurityUtils.currentUserId(), fileIds);
        // 异步执行打包
        transferService.executeZipTask(taskId);
        return Result.ok(Map.of("taskId", taskId));
    }

    @Operation(summary = "查询打包进度（完成后返回下载链接）")
    @GetMapping("/zip/{taskId}")
    public Result<ZipTaskVO> zipStatus(@PathVariable String taskId) {
        return Result.ok(transferService.zipStatus(SecurityUtils.currentUserId(), taskId));
    }

    @Operation(summary = "下载打包完成的 ZIP 文件")
    @GetMapping("/zip/file/{taskId}")
    public void downloadZip(@PathVariable String taskId, HttpServletResponse response) {
        transferService.downloadZip(SecurityUtils.currentUserId(), taskId, response);
    }
}
