package com.rabbit.pan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 注册口令码表
 *
 * <p>口令码与 12 位短链采用同款加密算法：雪花 ID → 盐值混淆 → Base62 定长 12 位。
 * 由管理员在后台生成，注册时必须提供有效口令码（一码一用）。</p>
 */
@Data
@TableName("invite_code")
public class InviteCode {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 雪花算法生成的口令码唯一 ID */
    private Long codeId;

    /** 12 位口令码（唯一索引） */
    private String code;

    /** 生成者（管理员）用户 ID */
    private Long createUserId;

    /** 使用该口令码注册的用户 ID */
    private Long usedBy;

    /** 使用时间 */
    private Date usedTime;

    /** 状态：1 可用 0 已使用/已作废 */
    private Integer status;

    /** 过期时间，null 为永久有效 */
    private Date expireTime;

    private Date createTime;
}
