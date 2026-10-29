package com.rabbit.pan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 秒传校验请求
 */
@Data
public class UploadCheckDTO {

    @NotBlank(message = "文件名不能为空")
    private String fileName;

    @NotNull(message = "文件大小不能为空")
    private Long fileSize;

    @NotBlank(message = "文件 MD5 不能为空")
    private String md5;

    /** 目标父目录 ID，根目录传 0 */
    @NotNull(message = "目标目录不能为空")
    private Long parentDirId;
}
