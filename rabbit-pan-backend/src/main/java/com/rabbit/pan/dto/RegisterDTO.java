package com.rabbit.pan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 用户注册请求
 */
@Data
public class RegisterDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 4, max = 32, message = "用户名长度需在 4-32 位之间")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "用户名仅支持字母、数字、下划线和短横线")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需在 6-32 位之间")
    private String password;

    @Size(max = 32, message = "昵称最长 32 位")
    private String nickname;

    /** 注册口令码（管理员后台生成，一码一用） */
    @NotBlank(message = "口令码不能为空")
    private String inviteCode;
}
