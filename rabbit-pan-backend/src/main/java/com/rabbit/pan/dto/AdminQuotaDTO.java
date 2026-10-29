package com.rabbit.pan.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理员修改用户配额请求
 */
@Data
public class AdminQuotaDTO {

    @NotNull(message = "用户 ID 不能为空")
    private Long userId;

    @NotNull(message = "配额不能为空")
    @Min(value = 1, message = "配额至少 1GB")
    @Max(value = 1024 * 100, message = "配额过大")
    private Integer quotaGb;
}
