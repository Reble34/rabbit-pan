package com.rabbit.pan.controller;

import com.rabbit.pan.common.Result;
import com.rabbit.pan.entity.SysUser;
import com.rabbit.pan.mapper.SysUserMapper;
import com.rabbit.pan.security.SecurityUtils;
import com.rabbit.pan.service.UserService;
import com.rabbit.pan.vo.StorageVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 存储空间接口
 */
@Tag(name = "存储空间")
@RestController
@RequestMapping("/api/v1/storage")
@RequiredArgsConstructor
public class StorageController {

    private final UserService userService;
    private final SysUserMapper sysUserMapper;

    @Operation(summary = "存储使用情况（已用/总/剩余/文件数量）")
    @GetMapping("/usage")
    public Result<StorageVO> usage() {
        SysUser user = sysUserMapper.selectById(SecurityUtils.currentUserId());
        return Result.ok(userService.storageUsage(user));
    }
}
