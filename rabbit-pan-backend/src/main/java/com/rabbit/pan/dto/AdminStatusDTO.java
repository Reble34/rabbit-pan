package com.rabbit.pan.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理员修改用户状态请求
 */
@Data
public class AdminStatusDTO {

    @NotNull(message = "用户 ID 不能为空")
    private Long userId;

    /** 1 启用 0 禁用 */
    @NotNull(message = "状态不能为空")
    @Min(0)
    @Max(1)
    private Integer status;
}
