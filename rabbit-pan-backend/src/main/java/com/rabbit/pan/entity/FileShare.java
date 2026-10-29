package com.rabbit.pan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 文件分享表（12 位加密短链）
 */
@Data
@TableName("file_share")
public class FileShare {

    /** 自增主键 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 雪花算法生成的分享唯一 ID */
    private Long shareId;

    /** 12 位短链接编码（唯一索引） */
    private String shortCode;

    /** 分享用户 ID */
    private Long userId;

    /** 分享的文件/文件夹 ID */
    private Long fileId;

    /** 分享类型：1 文件 2 文件夹 */
    private Integer shareType;

    /** 提取码（MD5 存储），为空则公开访问 */
    private String password;

    /** 过期时间，null 为永久有效 */
    private Date expireTime;

    /** 最大访问次数，-1 为不限制 */
    private Integer maxView;

    /** 累计访问次数 */
    private Integer viewCount;

    /** 是否允许下载：1 可下载 0 仅预览 */
    private Integer canDownload;

    /** 状态：1 有效 0 已撤销 */
    private Integer status;

    private Date createTime;

    private Date updateTime;
}
