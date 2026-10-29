package com.rabbit.pan.vo;

import lombok.Data;

/**
 * ZIP 打包任务视图对象
 */
@Data
public class ZipTaskVO {

    /** 状态：0 处理中 1 完成 2 失败 */
    private Integer status;
    /** 完成后的下载地址 */
    private String url;
    /** 任务属主（下载/查询时校验，防止越权） */
    private Long ownerUserId;
}
