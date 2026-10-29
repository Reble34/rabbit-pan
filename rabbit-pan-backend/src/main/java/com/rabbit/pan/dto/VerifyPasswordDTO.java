package com.rabbit.pan.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 分享提取码校验请求
 */
@Data
public class VerifyPasswordDTO {

    @NotBlank(message = "提取码不能为空")
    private String password;
}
