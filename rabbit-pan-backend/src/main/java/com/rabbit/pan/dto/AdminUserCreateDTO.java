package com.rabbit.pan.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 管理员新增用户请求
 */
@Data
public class AdminUserCreateDTO {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 4, max = 32, message = "用户名长度需在 4-32 位之间")
    private String username;

    @NotBlank(message = "初始密码不能为空")
    @Size(min = 6, max = 32, message = "密码长度需在 6-32 位之间")
    private String password;

    @Size(max = 32, message = "昵称最长 32 位")
    private String nickname;

    /** 初始存储配额（GB），为空则使用系统默认配额 */
    @Min(value = 1, message = "配额至少 1GB")
    @Max(value = 1024 * 100, message = "配额过大")
    private Integer quotaGb;
}
