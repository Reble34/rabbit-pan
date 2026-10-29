package com.rabbit.pan.security;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 登录用户主体（从 JWT Claims 构建，避免每次请求查库）
 */
@Data
@AllArgsConstructor
public class LoginUser {

    private Long userId;
    private String username;
    private String role;
}
