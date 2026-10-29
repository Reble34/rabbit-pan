package com.rabbit.pan.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 系统配置表（单文件上限、允许类型、回收站保留天数、默认配额等）
 */
@Data
@TableName("sys_config")
public class SysConfig {

    /** 配置键 */
    @TableId(type = IdType.INPUT)
    private String configKey;

    /** 配置值 */
    private String configValue;

    /** 配置说明 */
    private String description;
}
