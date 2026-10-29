package com.rabbit.pan.vo;

import lombok.Data;

/**
 * 系统配置视图对象
 */
@Data
public class ConfigVO {

    /** 单文件大小上限（MB） */
    private Integer maxFileMb;
    /** 允许上传的扩展名（逗号分隔） */
    private String allowedExts;
    /** 回收站保留天数 */
    private Integer recycleKeepDays;
    /** 新用户默认配额（GB） */
    private Integer defaultQuotaGb;

    /** 系统总存储空间（GB，全用户共享上限） */
    private Integer totalStorageGb;
}
