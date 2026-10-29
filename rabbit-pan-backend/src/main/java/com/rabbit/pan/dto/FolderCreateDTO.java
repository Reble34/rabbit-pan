package com.rabbit.pan.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 新建文件夹请求
 */
@Data
public class FolderCreateDTO {

    /** 父目录 ID，根目录传 0 */
    @NotNull(message = "父目录不能为空")
    private Long parentId;

    @NotBlank(message = "文件夹名称不能为空")
    @Size(max = 128, message = "名称最长 128 位")
    private String fileName;
}
