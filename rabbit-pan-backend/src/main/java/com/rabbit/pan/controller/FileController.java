package com.rabbit.pan.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rabbit.pan.common.Result;
import com.rabbit.pan.dto.BatchDTO;
import com.rabbit.pan.dto.FolderCreateDTO;
import com.rabbit.pan.dto.MoveDTO;
import com.rabbit.pan.dto.RenameDTO;
import com.rabbit.pan.entity.FileInfo;
import com.rabbit.pan.security.SecurityUtils;
import com.rabbit.pan.service.FileService;
import com.rabbit.pan.service.OpLogService;
import com.rabbit.pan.vo.FileVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 文件与目录管理接口
 */
@Tag(name = "文件与目录管理")
@RestController
@RequestMapping("/api/v1/file")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;
    private final OpLogService opLogService;

    @Operation(summary = "获取目录列表（支持排序/搜索/筛选）")
    @GetMapping("/list")
    public Result<Page<FileVO>> list(
            @RequestParam(defaultValue = "0") Long parentId,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date endTime) {
        Page<FileInfo> page = fileService.list(SecurityUtils.currentUserId(), parentId, pageNum, pageSize,
                sortField, sortOrder, keyword, type, startTime, endTime);
        Page<FileVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(fileService::toFileVO).toList());
        return Result.ok(result);
    }

    @Operation(summary = "新建文件夹")
    @PostMapping("/folder")
    public Result<FileVO> createFolder(@Valid @RequestBody FolderCreateDTO dto) {
        FileInfo folder = fileService.createFolder(SecurityUtils.currentUserId(), dto);
        return Result.ok(fileService.toFileVO(folder));
    }

    @Operation(summary = "重命名（校验重名）")
    @PutMapping("/rename")
    public Result<Void> rename(@Valid @RequestBody RenameDTO dto) {
        fileService.rename(SecurityUtils.currentUserId(), dto.getFileId(), dto.getFileName());
        return Result.ok();
    }

    @Operation(summary = "批量移动（校验目标权限与子目录环）")
    @PutMapping("/move")
    public Result<Void> move(@Valid @RequestBody MoveDTO dto) {
        fileService.move(SecurityUtils.currentUserId(), dto);
        return Result.ok();
    }

    @Operation(summary = "批量复制（文件夹递归复制）")
    @PostMapping("/copy")
    public Result<Map<String, Integer>> copy(@Valid @RequestBody MoveDTO dto) {
        int count = fileService.copy(SecurityUtils.currentUserId(), dto);
        return Result.ok(Map.of("copied", count));
    }

    @Operation(summary = "删除（软删，移入回收站）")
    @DeleteMapping("/delete")
    public Result<Map<String, Integer>> delete(@RequestParam List<Long> fileIds) {
        int count = fileService.softDelete(SecurityUtils.currentUserId(), fileIds);
        opLogService.record(SecurityUtils.currentUserId(), "DELETE",
                "移入回收站 " + count + " 项", null);
        return Result.ok(Map.of("deleted", count));
    }

    @Operation(summary = "批量操作（删除/移动/复制）")
    @PostMapping("/batch")
    public Result<Map<String, Integer>> batch(@Valid @RequestBody BatchDTO dto) {
        Long userId = SecurityUtils.currentUserId();
        switch (dto.getOperation() == null ? "" : dto.getOperation()) {
            case "delete" -> {
                int count = fileService.softDelete(userId, dto.getFileIds());
                return Result.ok(Map.of("deleted", count));
            }
            case "move" -> {
                MoveDTO move = new MoveDTO();
                move.setFileIds(dto.getFileIds());
                move.setTargetParentId(dto.getTargetParentId());
                fileService.move(userId, move);
                return Result.ok(Map.of("moved", dto.getFileIds().size()));
            }
            case "copy" -> {
                MoveDTO copy = new MoveDTO();
                copy.setFileIds(dto.getFileIds());
                copy.setTargetParentId(dto.getTargetParentId());
                int count = fileService.copy(userId, copy);
                return Result.ok(Map.of("copied", count));
            }
            default -> throw new com.rabbit.pan.common.BizException(
                    com.rabbit.pan.common.ResultCode.PARAM_ERROR, "不支持的操作类型");
        }
    }

    @Operation(summary = "文件搜索（模糊 + 类型/时间筛选，全局递归）")
    @GetMapping("/search")
    public Result<Page<FileVO>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") Date endTime,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(required = false) String sortField,
            @RequestParam(required = false) String sortOrder) {
        Page<FileInfo> page = fileService.list(SecurityUtils.currentUserId(), 0L, pageNum, pageSize,
                sortField, sortOrder, keyword, type, startTime, endTime);
        Page<FileVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(fileService::toFileVO).toList());
        return Result.ok(result);
    }

    @Operation(summary = "文件详情（完整元数据）")
    @GetMapping("/{id}")
    public Result<FileVO> detail(@PathVariable Long id) {
        FileInfo file = fileService.getOwned(SecurityUtils.currentUserId(), id);
        return Result.ok(fileService.toFileVO(file));
    }
}
