package com.rabbit.pan.controller;

import com.rabbit.pan.common.Result;
import com.rabbit.pan.dto.UploadCheckDTO;
import com.rabbit.pan.dto.UploadInitDTO;
import com.rabbit.pan.dto.UploadMergeDTO;
import com.rabbit.pan.security.SecurityUtils;
import com.rabbit.pan.service.OpLogService;
import com.rabbit.pan.service.UploadService;
import com.rabbit.pan.vo.UploadInitVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 上传接口：秒传校验 / 分片上传（断点续传）/ 单文件直传
 */
@Tag(name = "上传")
@RestController
@RequestMapping("/api/v1/upload")
@RequiredArgsConstructor
public class UploadController {

    private final UploadService uploadService;
    private final OpLogService opLogService;

    @Operation(summary = "秒传校验（MD5 命中直接完成）")
    @PostMapping("/check")
    public Result<Map<String, Object>> check(@Valid @RequestBody UploadCheckDTO dto) {
        return Result.ok(uploadService.check(SecurityUtils.currentUserId(), dto));
    }

    @Operation(summary = "初始化分片（返回 uploadId/分片大小/已传分片）")
    @PostMapping("/init")
    public Result<UploadInitVO> init(@Valid @RequestBody UploadInitDTO dto) {
        return Result.ok(uploadService.init(SecurityUtils.currentUserId(), dto));
    }

    @Operation(summary = "断点续传（携带原 uploadId 查询已传分片）")
    @PostMapping("/resume")
    public Result<UploadInitVO> resume(@RequestParam String uploadId,
                                       @Valid @RequestBody UploadInitDTO dto) {
        return Result.ok(uploadService.resume(SecurityUtils.currentUserId(), uploadId, dto));
    }

    @Operation(summary = "上传分片")
    @PostMapping(value = "/chunk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Void> chunk(@RequestParam String uploadId,
                              @RequestParam Integer chunkIndex,
                              @RequestParam("file") MultipartFile file) {
        uploadService.saveChunk(SecurityUtils.currentUserId(), uploadId, chunkIndex, file);
        return Result.ok();
    }

    @Operation(summary = "合并分片（合并落库）")
    @PostMapping("/merge")
    public Result<Map<String, Object>> merge(@Valid @RequestBody UploadMergeDTO dto) {
        Long fileId = uploadService.merge(SecurityUtils.currentUserId(), dto);
        opLogService.record(SecurityUtils.currentUserId(), "UPLOAD",
                "分片上传完成: " + dto.getFileName(), null);
        return Result.ok(Map.of("fileId", fileId));
    }

    @Operation(summary = "取消上传（清理已传分片）")
    @DeleteMapping("/{uploadId}")
    public Result<Void> cancel(@PathVariable String uploadId) {
        uploadService.cancel(SecurityUtils.currentUserId(), uploadId);
        return Result.ok();
    }

    @Operation(summary = "单文件直传（小文件）")
    @PostMapping(value = "/single", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, Object>> single(@RequestParam("file") MultipartFile file,
                                              @RequestParam(defaultValue = "0") Long parentDirId) {
        Long fileId = uploadService.singleUpload(SecurityUtils.currentUserId(), parentDirId, file);
        opLogService.record(SecurityUtils.currentUserId(), "UPLOAD",
                "直传文件: " + file.getOriginalFilename(), null);
        return Result.ok(Map.of("fileId", fileId));
    }
}
