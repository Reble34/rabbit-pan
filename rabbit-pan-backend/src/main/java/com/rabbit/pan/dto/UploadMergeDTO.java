package com.rabbit.pan.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 合并分片请求
 */
@Data
public class UploadMergeDTO {

    @NotBlank(message = "uploadId 不能为空")
    private String uploadId;

    @jakarta.validation.constraints.NotBlank(message = "文件名不能为空")
    private String fileName;

    @jakarta.validation.constraints.NotNull(message = "文件大小不能为空")
    private Long fileSize;

    @jakarta.validation.constraints.NotBlank(message = "文件 MD5 不能为空")
    private String md5;

    @jakarta.validation.constraints.NotNull(message = "目标目录不能为空")
    private Long parentDirId;
}
