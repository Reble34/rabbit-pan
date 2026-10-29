package com.rabbit.pan.vo;

import lombok.Data;

/**
 * 存储使用情况视图对象
 */
@Data
public class StorageVO {

    /** 已用容量（字节） */
    private Long used;
    /** 配额总容量（字节） */
    private Long total;
    /** 剩余容量（字节） */
    private Long remain;
    /** 文件数量 */
    private Long fileCount;
    /** 使用百分比（0-100，保留两位小数） */
    private Double percent;
}
