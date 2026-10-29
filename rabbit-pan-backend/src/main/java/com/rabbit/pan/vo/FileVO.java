package com.rabbit.pan.vo;

import lombok.Data;

import java.util.Date;

/**
 * 文件/文件夹视图对象
 */
@Data
public class FileVO {

    private Long id;
    private Long parentId;
    private String fileName;
    private Boolean isDir;
    private Long size;
    /** 类型分类：image/video/audio/pdf/txt/md/doc/zip/other */
    private String fileType;
    private String ext;
    /** 文件内容 MD5（文件夹为空） */
    private String md5;
    private Date createTime;
    private Date updateTime;
    /** 回收站场景专用：删除时间与剩余保留天数 */
    private Date deleteTime;
    private Long recycleDaysLeft;
}
