package com.rabbit.pan.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rabbit.pan.common.Result;
import com.rabbit.pan.dto.ShareCreateDTO;
import com.rabbit.pan.dto.ShareUpdateDTO;
import com.rabbit.pan.security.SecurityUtils;
import com.rabbit.pan.service.OpLogService;
import com.rabbit.pan.service.ShareService;
import com.rabbit.pan.vo.ShareVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 分享管理接口（需登录）
 */
@Tag(name = "分享管理")
@RestController
@RequestMapping("/api/v1/share")
@RequiredArgsConstructor
public class ShareController {

    private final ShareService shareService;
    private final OpLogService opLogService;

    @Operation(summary = "生成分享链接（12 位加密短码）")
    @PostMapping("/create")
    public Result<ShareVO> create(@Valid @RequestBody ShareCreateDTO dto) {
        ShareVO vo = shareService.create(SecurityUtils.currentUserId(), dto);
        opLogService.record(SecurityUtils.currentUserId(), "SHARE",
                "创建分享: " + vo.getFileName(), null);
        return Result.ok(vo);
    }

    @Operation(summary = "我的分享列表（分页，含访问次数与状态）")
    @GetMapping("/list")
    public Result<Page<ShareVO>> list(@RequestParam(defaultValue = "1") int pageNum,
                                      @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(shareService.listMine(SecurityUtils.currentUserId(), pageNum, pageSize));
    }

    @Operation(summary = "撤销分享（立即失效，同步删 Redis）")
    @DeleteMapping("/{id}")
    public Result<Void> revoke(@PathVariable Long id) {
        shareService.revoke(SecurityUtils.currentUserId(), id);
        return Result.ok();
    }

    @Operation(summary = "更新分享配置（改有效期/提取码/权限，短码不变）")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ShareUpdateDTO dto) {
        shareService.update(SecurityUtils.currentUserId(), id, dto);
        return Result.ok();
    }
}
