package com.rabbit.pan.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rabbit.pan.common.BizException;
import com.rabbit.pan.common.Constants;
import com.rabbit.pan.common.ResultCode;
import com.rabbit.pan.dto.ChangePasswordDTO;
import com.rabbit.pan.dto.LoginDTO;
import com.rabbit.pan.dto.RegisterDTO;
import com.rabbit.pan.entity.SysUser;
import com.rabbit.pan.mapper.SysUserMapper;
import com.rabbit.pan.security.JwtUtil;
import com.rabbit.pan.util.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Set;

/**
 * 认证服务：注册 / 登录 / 双令牌刷新 / 登出 / 修改密码
 * <p>登录失败超过阈值临时锁定；密码 BCrypt 加密；Redis 维护登录态。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final SysUserMapper sysUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final RedisService redisService;
    private final ConfigService configService;
    private final OpLogService opLogService;
    private final UserService userService;
    private final InviteCodeService inviteCodeService;

    /** 登录并签发双令牌（组装 LoginVO） */
    public com.rabbit.pan.vo.LoginVO loginAndIssue(LoginDTO dto, HttpServletRequest request) {
        SysUser user = login(dto, request);
        return issueTokens(user);
    }

    /** 刷新令牌：校验 refreshToken 后轮换新双令牌 */
    public com.rabbit.pan.vo.LoginVO refresh(String refreshToken) {
        Long userId = validateRefreshToken(refreshToken);
        rotateRefreshToken(refreshToken);
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || (user.getStatus() != null
                && user.getStatus() == Constants.USER_STATUS_DISABLED)) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return issueTokens(user);
    }

    private com.rabbit.pan.vo.LoginVO issueTokens(SysUser user) {
        String accessToken = jwtUtil.createAccessToken(user.getId(), user.getUsername(), user.getRole());
        String refreshToken = jwtUtil.createRefreshToken(user.getId(), user.getUsername(), user.getRole());
        saveLoginState(user.getId(), accessToken, refreshToken);
        com.rabbit.pan.vo.LoginVO vo = new com.rabbit.pan.vo.LoginVO();
        vo.setAccessToken(accessToken);
        vo.setRefreshToken(refreshToken);
        vo.setAccessExpiresIn(jwtUtil.getAccessExpireMillis() / 1000);
        vo.setUser(userService.toUserVO(user));
        return vo;
    }

    /**
     * 注册（需管理员生成的口令码，一码一用）
     * <p>流程：用户名唯一性检查 → 创建用户（默认配额 default_quota_gb，当前 5GB）→ 同事务原子消费口令码。</p>
     */
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void register(RegisterDTO dto, String ip) {
        Long exists = sysUserMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.getUsername()));
        if (exists != null && exists > 0) {
            throw new BizException(ResultCode.USERNAME_EXISTS);
        }
        SysUser user = new SysUser();
        user.setUsername(dto.getUsername());
        user.setPassword(passwordEncoder.encode(dto.getPassword()));
        user.setNickname(dto.getNickname() == null || dto.getNickname().isBlank()
                ? dto.getUsername() : dto.getNickname());
        user.setRole(Constants.ROLE_USER);
        user.setStatus(Constants.USER_STATUS_ENABLED);
        long defaultQuotaBytes = configService.getInt(ConfigService.KEY_DEFAULT_QUOTA_GB, 5) * 1024L * 1024 * 1024;
        user.setStorageTotal(defaultQuotaBytes);
        sysUserMapper.insert(user);
        // 原子消费口令码（一码一用）；消费失败抛异常，注册整体回滚
        inviteCodeService.consume(dto.getInviteCode(), user.getId());
        opLogService.record(user.getId(), "REGISTER", "新用户注册: " + user.getUsername(), ip);
        log.info("用户注册成功: userId={}, username={}", user.getId(), user.getUsername());
    }

    /** 登录：校验密码与状态，签发双令牌并写入 Redis 登录态 */
    public SysUser login(LoginDTO dto, HttpServletRequest request) {
        String ip = IpUtil.getClientIp(request);
        // 锁定检查前置：锁定期间无论密码正确与否一律拒绝，杜绝绕过语义
        checkLoginLock(dto.getUsername());
        SysUser user = sysUserMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, dto.getUsername()));
        if (user == null || !passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            recordLoginFail(dto.getUsername());
            opLogService.record(user == null ? 0 : user.getId(), "LOGIN_FAIL",
                    "登录失败: " + dto.getUsername(), ip);
            throw new BizException(ResultCode.PASSWORD_ERROR);
        }
        if (user.getStatus() != null && user.getStatus() == Constants.USER_STATUS_DISABLED) {
            throw new BizException(ResultCode.LOGIN_DISABLED);
        }
        redisService.delete(Constants.RedisKeys.LOGIN_FAIL + dto.getUsername());
        opLogService.record(user.getId(), "LOGIN", "用户登录: " + user.getUsername(), ip);
        log.info("用户登录成功: userId={}, username={}", user.getId(), user.getUsername());
        return user;
    }

    /** 保存登录令牌到 Redis */
    public void saveLoginState(Long userId, String accessToken, String refreshToken) {
        redisService.set(Constants.RedisKeys.LOGIN_TOKEN + accessToken, String.valueOf(userId),
                Duration.ofMillis(jwtUtil.getAccessExpireMillis()));
        redisService.set(Constants.RedisKeys.REFRESH_TOKEN + refreshToken, String.valueOf(userId),
                Duration.ofMillis(jwtUtil.getRefreshExpireMillis()));
        redisService.sAdd(Constants.RedisKeys.USER_TOKENS + userId, accessToken,
                Duration.ofMillis(jwtUtil.getAccessExpireMillis()));
        redisService.sAdd(Constants.RedisKeys.USER_TOKENS + userId, refreshToken,
                Duration.ofMillis(jwtUtil.getRefreshExpireMillis()));
    }

    /** 刷新令牌：校验 refreshToken 后轮换新双令牌 */
    public Long validateRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        Long userId = null;
        try {
            var claims = jwtUtil.parse(refreshToken);
            if (!jwtUtil.isRefreshToken(claims)) {
                throw new BizException(ResultCode.UNAUTHORIZED);
            }
            userId = Long.valueOf(claims.getSubject());
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        String cached = redisService.get(Constants.RedisKeys.REFRESH_TOKEN + refreshToken);
        if (cached == null || !cached.equals(String.valueOf(userId))) {
            throw new BizException(ResultCode.UNAUTHORIZED);
        }
        return userId;
    }

    /** 轮换：删除旧 refreshToken 登录态 */
    public void rotateRefreshToken(String oldRefreshToken) {
        redisService.delete(Constants.RedisKeys.REFRESH_TOKEN + oldRefreshToken);
    }

    /** 登出：使当前 accessToken 与可选 refreshToken 失效 */
    public void logout(Long userId, String accessToken, String refreshToken, String ip) {
        redisService.delete(Constants.RedisKeys.LOGIN_TOKEN + accessToken);
        if (refreshToken != null && !refreshToken.isBlank()) {
            redisService.delete(Constants.RedisKeys.REFRESH_TOKEN + refreshToken);
            redisService.sRemove(Constants.RedisKeys.USER_TOKENS + userId, refreshToken);
        }
        redisService.sRemove(Constants.RedisKeys.USER_TOKENS + userId, accessToken);
        opLogService.record(userId, "LOGOUT", "用户登出", ip);
    }

    /** 修改密码：校验旧密码，成功后强制全部会话下线 */
    public void changePassword(Long userId, ChangePasswordDTO dto, String ip) {
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ResultCode.NOT_FOUND);
        }
        if (!passwordEncoder.matches(dto.getOldPassword(), user.getPassword())) {
            throw new BizException(ResultCode.OLD_PASSWORD_ERROR);
        }
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        sysUserMapper.updateById(user);
        // 强制下线全部会话
        Set<String> tokens = redisService.sMembers(Constants.RedisKeys.USER_TOKENS + userId);
        if (tokens != null) {
            redisService.deleteAll(tokens.stream()
                    .map(t -> Constants.RedisKeys.LOGIN_TOKEN + t).toList());
            redisService.deleteAll(tokens.stream()
                    .map(t -> Constants.RedisKeys.REFRESH_TOKEN + t).toList());
        }
        redisService.delete(Constants.RedisKeys.USER_TOKENS + userId);
        opLogService.record(userId, "CHANGE_PASSWORD", "修改密码", ip);
        log.info("用户修改密码并强制下线: userId={}", userId);
    }

    /** 管理员禁用用户后强制下线其全部会话 */
    public void kickUser(Long userId) {
        Set<String> tokens = redisService.sMembers(Constants.RedisKeys.USER_TOKENS + userId);
        if (tokens != null) {
            redisService.deleteAll(tokens.stream()
                    .map(t -> Constants.RedisKeys.LOGIN_TOKEN + t).toList());
            redisService.deleteAll(tokens.stream()
                    .map(t -> Constants.RedisKeys.REFRESH_TOKEN + t).toList());
        }
        redisService.delete(Constants.RedisKeys.USER_TOKENS + userId);
    }

    private void checkLoginLock(String username) {
        String failCount = redisService.get(Constants.RedisKeys.LOGIN_FAIL + username);
        if (failCount != null && Integer.parseInt(failCount) >= Constants.LOGIN_FAIL_LIMIT) {
            throw new BizException(ResultCode.ACCOUNT_LOCKED);
        }
    }

    private void recordLoginFail(String username) {
        Long count = redisService.increment(Constants.RedisKeys.LOGIN_FAIL + username,
                Duration.ofMinutes(Constants.LOGIN_LOCK_MINUTES));
        log.warn("登录失败: username={}, 连续失败次数={}", username, count);
    }
}
