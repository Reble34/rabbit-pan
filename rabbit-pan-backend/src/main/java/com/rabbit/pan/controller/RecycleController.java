package com.rabbit.pan.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rabbit.pan.common.Result;
import com.rabbit.pan.dto.RestoreDTO;
import com.rabbit.pan.security.SecurityUtils;
import com.rabbit.pan.service.OpLogService;
import com.rabbit.pan.service.RecycleService;
import com.rabbit.pan.vo.FileVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 回收站接口
 */
@Tag(name = "回收站")
@RestController
@RequestMapping("/api/v1/recycle")
@RequiredArgsConstructor
public class RecycleController {

    private final RecycleService recycleService;
    private final OpLogService opLogService;

    @Operation(summary = "回收站列表（分页，按名称/删除时间筛选）")
    @GetMapping("/list")
    public Result<Page<FileVO>> list(@RequestParam(defaultValue = "1") int pageNum,
                                     @RequestParam(defaultValue = "50") int pageSize,
                                     @RequestParam(required = false) String keyword) {
        Page<com.rabbit.pan.entity.FileInfo> page =
                recycleService.list(SecurityUtils.currentUserId(), pageNum, pageSize, keyword);
        Page<FileVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(recycleService::toVO).toList());
        return Result.ok(result);
    }

    @Operation(summary = "恢复文件（还原到原路径，重名则提示）")
    @PostMapping("/restore")
    public Result<Map<String, Integer>> restore(@Valid @RequestBody RestoreDTO dto) {
        int count = recycleService.restore(SecurityUtils.currentUserId(), dto.getFileIds());
        opLogService.record(SecurityUtils.currentUserId(), "RESTORE", "恢复文件 " + count + " 项", null);
        return Result.ok(Map.of("restored", count));
    }

    @Operation(summary = "永久删除（物理删除，释放磁盘）")
    @DeleteMapping("/delete")
    public Result<Map<String, Long>> deleteForever(@RequestBody List<Long> fileIds) {
        long count = recycleService.deleteForever(SecurityUtils.currentUserId(), fileIds);
        opLogService.record(SecurityUtils.currentUserId(), "DELETE_FOREVER",
                "永久删除 " + count + " 项", null);
        return Result.ok(Map.of("deleted", count));
    }

    @Operation(summary = "清空回收站")
    @DeleteMapping("/clear")
    public Result<Map<String, Long>> clear() {
        long count = recycleService.clear(SecurityUtils.currentUserId());
        opLogService.record(SecurityUtils.currentUserId(), "CLEAR_RECYCLE",
                "清空回收站 " + count + " 项", null);
        return Result.ok(Map.of("cleared", count));
    }
}
