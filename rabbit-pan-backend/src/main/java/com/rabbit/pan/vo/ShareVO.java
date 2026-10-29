package com.rabbit.pan.vo;

import lombok.Data;

import java.util.Date;

/**
 * 我的分享列表视图对象
 */
@Data
public class ShareVO {

    private Long id;
    private Long shareId;
    private String shortCode;
    /** 完整短链地址 */
    private String shortUrl;
    private Long fileId;
    private String fileName;
    private Long fileSize;
    private Boolean isDir;
    private Integer shareType;
    /** 是否设置了提取码 */
    private Boolean hasPassword;
    private Integer maxView;
    private Integer viewCount;
    private Boolean canDownload;
    private Date expireTime;
    private Integer status;
    private Date createTime;
}
