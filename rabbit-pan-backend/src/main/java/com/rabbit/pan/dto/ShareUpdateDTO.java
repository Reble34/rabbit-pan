package com.rabbit.pan.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 更新分享配置请求（短码保持不变）
 * <p>password 传 null 表示不修改；传空字符串表示清除提取码。</p>
 */
@Data
public class ShareUpdateDTO {

    /** 有效期（天）：0 表示永久 */
    @Min(value = 0, message = "有效期不能为负数")
    @Max(value = 3650, message = "有效期最长 3650 天")
    private Integer expireDays;

    @Pattern(regexp = "^$|^[a-zA-Z0-9]{4,6}$", message = "提取码需为 4-6 位字母或数字")
    private String password;

    @Min(value = -1, message = "最大访问次数不合法")
    private Integer maxView;

    private Boolean canDownload;
}
