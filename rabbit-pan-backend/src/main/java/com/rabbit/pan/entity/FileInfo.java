package com.rabbit.pan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 文件/文件夹元数据表
 *
 * <p>删除采用软删策略：status=1 表示在回收站；物理删除由回收站/管理端触发。</p>
 */
@Data
@TableName("file_info")
public class FileInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属用户 ID */
    private Long userId;

    /** 父目录 ID（根目录为 0） */
    private Long parentId;

    /** 文件或文件夹名称 */
    private String fileName;

    /** 是否文件夹：true 文件夹 / false 文件 */
    private Boolean isDir;

    /** 文件大小（字节），文件夹恒为 0 */
    private Long size;

    /** 类型分类：image/video/audio/pdf/txt/md/doc/zip/other */
    private String fileType;

    /** 扩展名（小写，无点） */
    private String ext;

    /** MinIO 对象键（文件夹为空） */
    private String storagePath;

    /** 文件内容 MD5（秒传依据，文件夹为空） */
    private String md5;

    /** 状态：0 正常 1 回收站 */
    private Integer status;

    /** 进入回收站前的原父目录 ID（恢复用） */
    private Long originalParentId;

    /** 进入回收站时间 */
    private Date deleteTime;

    private Date createTime;

    private Date updateTime;
}
