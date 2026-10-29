package com.rabbit.pan.security;

import com.rabbit.pan.common.BizException;
import com.rabbit.pan.common.ResultCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 安全上下文工具：获取当前登录用户
 */
public final class SecurityUtils {

    public static LoginUser current() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof LoginUser loginUser) {
            return loginUser;
        }
        throw new BizException(ResultCode.UNAUTHORIZED);
    }

    public static Long currentUserId() {
        return current().getUserId();
    }

    private SecurityUtils() {
    }
}
