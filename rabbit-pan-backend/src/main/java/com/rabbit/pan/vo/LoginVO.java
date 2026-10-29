package com.rabbit.pan.vo;

import lombok.Data;

/**
 * 登录/刷新令牌响应
 */
@Data
public class LoginVO {

    private String accessToken;
    private String refreshToken;
    /** accessToken 有效时长（秒），供前端提前续期 */
    private Long accessExpiresIn;
    private UserVO user;
}
