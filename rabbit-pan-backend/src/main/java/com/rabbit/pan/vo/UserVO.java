package com.rabbit.pan.vo;

import lombok.Data;

/**
 * 用户信息视图对象
 */
@Data
public class UserVO {

    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String role;
    private Integer status;
    /** 已用容量（字节） */
    private Long storageUsed;
    /** 配额总容量（字节） */
    private Long storageTotal;
    /** 文件总数（不含文件夹） */
    private Long fileCount;
    private String createTime;
}
