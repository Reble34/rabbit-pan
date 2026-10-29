package com.rabbit.pan.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 管理员批量生成口令码请求
 */
@Data
public class InviteGenerateDTO {

    /** 生成数量：1-50 */
    @NotNull(message = "生成数量不能为空")
    @Min(value = 1, message = "每次至少生成 1 个")
    @Max(value = 50, message = "每次最多生成 50 个")
    private Integer count;
}
