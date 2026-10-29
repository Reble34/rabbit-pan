package com.rabbit.pan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 重命名请求
 */
@Data
public class RenameDTO {

    @NotNull(message = "文件 ID 不能为空")
    private Long fileId;

    @NotBlank(message = "新名称不能为空")
    @Size(max = 128, message = "名称最长 128 位")
    private String fileName;
}
