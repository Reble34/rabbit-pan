package com.rabbit.pan.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 创建分享请求
 */
@Data
public class ShareCreateDTO {

    @NotNull(message = "文件 ID 不能为空")
    private Long fileId;

    /** 有效期（天）：0 表示永久有效 */
    @NotNull(message = "有效期不能为空")
    @Min(value = 0, message = "有效期不能为负数")
    @Max(value = 3650, message = "有效期最长 3650 天")
    private Integer expireDays;

    /** 提取码（4-6 位），为空则公开访问 */
    @Pattern(regexp = "^$|^[a-zA-Z0-9]{4,6}$", message = "提取码需为 4-6 位字母或数字")
    private String password;

    /** 最大访问次数，-1 为不限制 */
    @Min(value = -1, message = "最大访问次数不合法")
    private Integer maxView = -1;

    /** 是否允许下载：false 表示仅预览 */
    private Boolean canDownload = true;
}
