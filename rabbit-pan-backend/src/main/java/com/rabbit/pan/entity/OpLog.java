package com.rabbit.pan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 操作日志表（上传/删除/分享/下载/登录全留痕）
 */
@Data
@TableName("op_log")
public class OpLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 操作用户 ID（0 表示匿名/系统） */
    private Long userId;

    /** 动作：LOGIN/LOGOUT/UPLOAD/DELETE/RESTORE/SHARE/DOWNLOAD/ADMIN */
    private String action;

    /** 详情描述 */
    private String detail;

    /** 客户端 IP */
    private String ip;

    private Date createTime;
}
