package com.rabbit.pan.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.rabbit.pan.common.Result;
import com.rabbit.pan.dto.ChangePasswordDTO;
import com.rabbit.pan.dto.LoginDTO;
import com.rabbit.pan.dto.NicknameDTO;
import com.rabbit.pan.dto.RefreshDTO;
import com.rabbit.pan.dto.RegisterDTO;
import com.rabbit.pan.entity.OpLog;
import com.rabbit.pan.entity.SysUser;
import com.rabbit.pan.mapper.SysUserMapper;
import com.rabbit.pan.security.LoginUser;
import com.rabbit.pan.security.SecurityUtils;
import com.rabbit.pan.service.AuthService;
import com.rabbit.pan.service.OpLogService;
import com.rabbit.pan.service.UserService;
import com.rabbit.pan.util.IpUtil;
import com.rabbit.pan.vo.LoginVO;
import com.rabbit.pan.vo.UserVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

/**
 * 用户认证与账号接口
 */
@Tag(name = "用户认证与账号")
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final UserService userService;
    private final OpLogService opLogService;
    private final SysUserMapper sysUserMapper;

    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterDTO dto, HttpServletRequest request) {
        authService.register(dto, IpUtil.getClientIp(request));
        return Result.ok();
    }

    @Operation(summary = "用户登录（返回双令牌）")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto, HttpServletRequest request) {
        return Result.ok(authService.loginAndIssue(dto, request));
    }

    @Operation(summary = "刷新令牌（refreshToken 换新 accessToken）")
    @PostMapping("/refresh")
    public Result<LoginVO> refresh(@Valid @RequestBody RefreshDTO dto) {
        return Result.ok(authService.refresh(dto.getRefreshToken()));
    }

    @Operation(summary = "用户登出（失效令牌，清理 Redis 登录态）")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request,
                               @RequestBody(required = false) Map<String, String> body) {
        LoginUser loginUser = SecurityUtils.current();
        String accessToken = extractBearer(request);
        authService.logout(loginUser.getUserId(), accessToken,
                body == null ? null : body.get("refreshToken"), IpUtil.getClientIp(request));
        return Result.ok();
    }

    @Operation(summary = "修改密码（成功后全部会话下线）")
    @PutMapping("/password")
    public Result<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto,
                                       HttpServletRequest request) {
        authService.changePassword(SecurityUtils.currentUserId(), dto, IpUtil.getClientIp(request));
        return Result.ok();
    }

    @Operation(summary = "当前用户信息（含存储配额）")
    @GetMapping("/info")
    public Result<UserVO> info() {
        SysUser user = sysUserMapper.selectById(SecurityUtils.currentUserId());
        return Result.ok(userService.toUserVO(user));
    }

    @Operation(summary = "上传头像")
    @PutMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, String>> avatar(@RequestParam("file") MultipartFile file) {
        String url = userService.updateAvatar(SecurityUtils.currentUserId(), file);
        return Result.ok(Map.of("avatarUrl", url));
    }

    @Operation(summary = "获取用户头像（公开访问）")
    @GetMapping("/avatar/{userId}")
    public void avatarStream(@PathVariable Long userId, HttpServletResponse response) {
        userService.streamAvatar(userId, response);
    }

    @Operation(summary = "修改昵称")
    @PutMapping("/nickname")
    public Result<Void> nickname(@Valid @RequestBody NicknameDTO dto) {
        userService.updateNickname(SecurityUtils.currentUserId(), dto.getNickname());
        return Result.ok();
    }

    @Operation(summary = "个人操作日志")
    @GetMapping("/logs")
    public Result<Page<OpLog>> logs(@RequestParam(defaultValue = "1") int pageNum,
                                    @RequestParam(defaultValue = "10") int pageSize) {
        return Result.ok(opLogService.page(SecurityUtils.currentUserId(), pageNum, pageSize));
    }

    private String extractBearer(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        return bearer != null && bearer.startsWith("Bearer ") ? bearer.substring(7) : null;
    }
}
