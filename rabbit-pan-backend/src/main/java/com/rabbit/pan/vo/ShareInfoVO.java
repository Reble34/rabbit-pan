package com.rabbit.pan.vo;

import lombok.Data;

import java.util.Date;

/**
 * 分享基本信息视图对象（公开访问，仅暴露必要信息）
 */
@Data
public class ShareInfoVO {

    private String shortCode;
    /** 分享根文件/文件夹 ID（用于单文件分享直接预览下载） */
    private Long fileId;
    private String fileName;
    private Long fileSize;
    private Boolean isDir;
    /** 是否需要提取码 */
    private Boolean needPassword;
    private Date expireTime;
    /** 是否允许下载 */
    private Boolean canDownload;
    /** 分享者昵称 */
    private String ownerName;
}
