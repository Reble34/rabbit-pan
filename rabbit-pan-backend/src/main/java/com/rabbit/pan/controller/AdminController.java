package com.rabbit.pan.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rabbit.pan.common.Result;
import com.rabbit.pan.dto.AdminConfigDTO;
import com.rabbit.pan.dto.AdminQuotaDTO;
import com.rabbit.pan.dto.AdminStatusDTO;
import com.rabbit.pan.dto.AdminUserCreateDTO;
import com.rabbit.pan.dto.InviteGenerateDTO;
import com.rabbit.pan.entity.FileInfo;
import com.rabbit.pan.entity.InviteCode;
import com.rabbit.pan.security.SecurityUtils;
import com.rabbit.pan.service.AdminService;
import com.rabbit.pan.service.ConfigService;
import com.rabbit.pan.service.FileService;
import com.rabbit.pan.service.InviteCodeService;
import com.rabbit.pan.vo.ConfigVO;
import com.rabbit.pan.vo.FileVO;
import com.rabbit.pan.vo.StatsVO;
import com.rabbit.pan.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 管理员后台接口（仅 ADMIN 角色）
 */
@Tag(name = "管理员后台")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;
    private final ConfigService configService;
    private final FileService fileService;
    private final InviteCodeService inviteCodeService;

    @Operation(summary = "用户列表（分页，按用户名/状态筛选）")
    @GetMapping("/user/list")
    public Result<Page<UserVO>> userList(@RequestParam(defaultValue = "1") int pageNum,
                                         @RequestParam(defaultValue = "10") int pageSize,
                                         @RequestParam(required = false) String keyword,
                                         @RequestParam(required = false) Integer status) {
        return Result.ok(adminService.userList(pageNum, pageSize, keyword, status));
    }

    @Operation(summary = "新增用户（设初始密码与配额）")
    @PostMapping("/user")
    public Result<Long> createUser(@Valid @RequestBody AdminUserCreateDTO dto) {
        return Result.ok(adminService.createUser(dto));
    }

    @Operation(summary = "修改存储配额")
    @PutMapping("/user/quota")
    public Result<Void> updateQuota(@Valid @RequestBody AdminQuotaDTO dto) {
        adminService.updateQuota(dto.getUserId(), dto.getQuotaGb());
        return Result.ok();
    }

    @Operation(summary = "禁用/启用用户（禁用后无法登录）")
    @PutMapping("/user/status")
    public Result<Void> updateStatus(@Valid @RequestBody AdminStatusDTO dto) {
        adminService.updateStatus(dto);
        return Result.ok();
    }

    @Operation(summary = "删除用户（级联删除全部文件）")
    @DeleteMapping("/user/{id}")
    public Result<Void> deleteUser(@PathVariable Long id) {
        adminService.deleteUser(id);
        return Result.ok();
    }

    @Operation(summary = "全局文件检索（跨用户）")
    @GetMapping("/file/search")
    public Result<Page<FileVO>> globalSearch(@RequestParam(defaultValue = "1") int pageNum,
                                             @RequestParam(defaultValue = "20") int pageSize,
                                             @RequestParam(required = false) String keyword) {
        Page<FileInfo> page = adminService.globalFileSearch(pageNum, pageSize, keyword);
        Page<FileVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(fileService::toFileVO).toList());
        return Result.ok(result);
    }

    @Operation(summary = "删除违规文件（强制删除，记日志）")
    @DeleteMapping("/file/{id}")
    public Result<Void> deleteFile(@PathVariable Long id) {
        adminService.deleteFile(id);
        return Result.ok();
    }

    @Operation(summary = "获取系统配置")
    @GetMapping("/config")
    public Result<ConfigVO> getConfig() {
        return Result.ok(configService.getAll());
    }

    @Operation(summary = "修改系统配置")
    @PutMapping("/config")
    public Result<Void> updateConfig(@Valid @RequestBody AdminConfigDTO dto) {
        ConfigVO vo = configService.getAll();
        if (dto.getMaxFileMb() != null) {
            vo.setMaxFileMb(dto.getMaxFileMb());
        }
        if (dto.getAllowedExts() != null) {
            vo.setAllowedExts(dto.getAllowedExts());
        }
        if (dto.getRecycleKeepDays() != null) {
            vo.setRecycleKeepDays(dto.getRecycleKeepDays());
        }
        if (dto.getDefaultQuotaGb() != null) {
            vo.setDefaultQuotaGb(dto.getDefaultQuotaGb());
        }
        if (dto.getTotalStorageGb() != null) {
            vo.setTotalStorageGb(dto.getTotalStorageGb());
        }
        configService.update(vo);
        return Result.ok();
    }

    @Operation(summary = "数据概览统计（用户数/文件总量/存储使用率/今日访问）")
    @GetMapping("/statistics")
    public Result<StatsVO> statistics() {
        return Result.ok(adminService.statistics());
    }

    // ==================== 注册口令码 ====================

    @Operation(summary = "批量生成注册口令码（与短链同款加密算法，一码一用）")
    @PostMapping("/invite/generate")
    public Result<List<InviteCode>> generateInvite(@Valid @RequestBody InviteGenerateDTO dto) {
        return Result.ok(inviteCodeService.generate(SecurityUtils.currentUserId(), dto.getCount()));
    }

    @Operation(summary = "口令码列表（分页，按状态筛选）")
    @GetMapping("/invite/list")
    public Result<Page<InviteCode>> inviteList(@RequestParam(defaultValue = "1") int pageNum,
                                               @RequestParam(defaultValue = "10") int pageSize,
                                               @RequestParam(required = false) Integer status) {
        return Result.ok(inviteCodeService.page(pageNum, pageSize, status));
    }

    @Operation(summary = "作废口令码（仅可用状态可作废）")
    @DeleteMapping("/invite/{id}")
    public Result<Void> revokeInvite(@PathVariable Long id) {
        inviteCodeService.revoke(id);
        return Result.ok();
    }
}
