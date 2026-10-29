package com.rabbit.pan.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 管理员修改系统配置请求
 */
@Data
public class AdminConfigDTO {

    /** 单文件大小上限（MB） */
    @Min(1)
    @Max(102400)
    private Integer maxFileMb;

    /** 允许上传的扩展名，逗号分隔 */
    private String allowedExts;

    /** 回收站保留天数 */
    @Min(1)
    @Max(365)
    private Integer recycleKeepDays;

    /** 新用户默认配额（GB） */
    @Min(1)
    @Max(1024 * 100)
    private Integer defaultQuotaGb;

    /** 系统总存储空间（GB，全用户共享上限） */
    @Min(1)
    @Max(1024 * 100)
    private Integer totalStorageGb;
}
