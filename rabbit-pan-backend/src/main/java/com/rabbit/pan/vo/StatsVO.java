package com.rabbit.pan.vo;

import lombok.Data;

/**
 * 管理端数据统计视图对象
 */
@Data
public class StatsVO {

    /** 用户总数 */
    private Long userCount;
    /** 文件总数（不含文件夹） */
    private Long fileCount;
    /** 总存储用量（字节） */
    private Long storageUsed;
    /** 总配额（字节） */
    private Long storageTotal;
    /** 今日操作次数 */
    private Long todayOpCount;
    /** 今日登录次数 */
    private Long todayLoginCount;
}
