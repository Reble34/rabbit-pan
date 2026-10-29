package com.rabbit.pan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * 系统用户表
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 登录用户名（唯一） */
    private String username;

    /** BCrypt 加密密码 */
    private String password;

    /** 显示昵称 */
    private String nickname;

    /** 头像访问地址 */
    private String avatar;

    /** 角色：ADMIN / USER */
    private String role;

    /** 状态：1 启用 0 禁用 */
    private Integer status;

    /** 存储配额总容量（字节） */
    private Long storageTotal;

    private Date createTime;

    private Date updateTime;
}
