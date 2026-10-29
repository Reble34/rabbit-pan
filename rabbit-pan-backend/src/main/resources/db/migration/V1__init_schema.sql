-- ============================================================
-- Rabbit 网盘 · 初始建表脚本 (Flyway V1)
-- MySQL 8.0 / InnoDB / utf8mb4
-- ============================================================

-- ---------- 用户表 ----------
CREATE TABLE `sys_user` (
  `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username`      varchar(32)  NOT NULL COMMENT '登录用户名（唯一）',
  `password`      varchar(100) NOT NULL COMMENT 'BCrypt 加密密码',
  `nickname`      varchar(32)  DEFAULT NULL COMMENT '显示昵称',
  `avatar`        varchar(255) DEFAULT NULL COMMENT '头像访问地址',
  `role`          varchar(16)  NOT NULL DEFAULT 'USER' COMMENT '角色：ADMIN/USER',
  `status`        tinyint      NOT NULL DEFAULT '1' COMMENT '状态：1 启用 0 禁用',
  `storage_total` bigint       NOT NULL DEFAULT '53687091200' COMMENT '存储配额（字节），默认 50GB',
  `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- ---------- 文件元数据表 ----------
CREATE TABLE `file_info` (
  `id`                 bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`            bigint       NOT NULL COMMENT '所属用户 ID',
  `parent_id`          bigint       NOT NULL DEFAULT '0' COMMENT '父目录 ID（根目录为 0）',
  `file_name`          varchar(255) NOT NULL COMMENT '文件/文件夹名称',
  `is_dir`             tinyint(1)   NOT NULL DEFAULT '0' COMMENT '是否文件夹',
  `size`               bigint       NOT NULL DEFAULT '0' COMMENT '文件大小（字节）',
  `file_type`          varchar(16)  DEFAULT NULL COMMENT '类型分类 image/video/audio/pdf/txt/md/doc/zip/other',
  `ext`                varchar(16)  DEFAULT NULL COMMENT '扩展名（小写）',
  `storage_path`       varchar(512) DEFAULT NULL COMMENT 'MinIO 对象键（文件夹为空）',
  `md5`                varchar(32)  DEFAULT NULL COMMENT '文件内容 MD5（秒传依据）',
  `status`             tinyint      NOT NULL DEFAULT '0' COMMENT '状态：0 正常 1 回收站',
  `original_parent_id` bigint       DEFAULT NULL COMMENT '进入回收站前的父目录',
  `delete_time`        datetime     DEFAULT NULL COMMENT '进入回收站时间',
  `create_time`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_parent` (`user_id`, `parent_id`, `status`),
  KEY `idx_md5` (`md5`),
  KEY `idx_user_status_delete` (`user_id`, `status`, `delete_time`),
  KEY `idx_storage_path` (`storage_path`(191))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件元数据表';

-- ---------- 文件分享表（12 位加密短链） ----------
CREATE TABLE `file_share` (
  `id`            bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `share_id`      bigint       NOT NULL COMMENT '雪花算法生成的分享唯一ID',
  `short_code`    varchar(12)  NOT NULL COMMENT '12位短链接编码，唯一索引',
  `user_id`       bigint       NOT NULL COMMENT '分享用户ID',
  `file_id`       bigint       NOT NULL COMMENT '分享的文件/文件夹ID',
  `share_type`    tinyint      NOT NULL DEFAULT '1' COMMENT '1:文件 2:文件夹',
  `password`      varchar(32)  DEFAULT NULL COMMENT '提取码（MD5存储），为空则公开访问',
  `expire_time`   datetime     DEFAULT NULL COMMENT '过期时间，null为永久有效',
  `max_view`      int          DEFAULT '-1' COMMENT '最大访问次数，-1为不限制',
  `view_count`    int          NOT NULL DEFAULT '0' COMMENT '累计访问次数',
  `can_download`  tinyint      NOT NULL DEFAULT '1' COMMENT '1:可下载 0:仅预览',
  `status`        tinyint      NOT NULL DEFAULT '1' COMMENT '1:有效 0:已撤销',
  `create_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_share_id` (`share_id`),
  UNIQUE KEY `uk_short_code` (`short_code`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='文件分享表';

-- ---------- 系统配置表 ----------
CREATE TABLE `sys_config` (
  `config_key`   varchar(64)  NOT NULL COMMENT '配置键',
  `config_value` varchar(512) DEFAULT NULL COMMENT '配置值',
  `description`  varchar(128) DEFAULT NULL COMMENT '配置说明',
  PRIMARY KEY (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

-- ---------- 操作日志表 ----------
CREATE TABLE `op_log` (
  `id`          bigint       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`     bigint       NOT NULL DEFAULT '0' COMMENT '操作用户 ID（0 匿名/系统）',
  `action`      varchar(32)  NOT NULL COMMENT '动作：LOGIN/LOGOUT/UPLOAD/DELETE/SHARE/DOWNLOAD 等',
  `detail`      varchar(512) DEFAULT NULL COMMENT '详情描述',
  `ip`          varchar(64)  DEFAULT NULL COMMENT '客户端 IP',
  `create_time` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`, `create_time`),
  KEY `idx_action_time` (`action`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';

-- ---------- 系统配置初始数据 ----------
INSERT INTO `sys_config` (`config_key`, `config_value`, `description`) VALUES
  ('max_file_mb',       '2048',                                        '单文件大小上限(MB)'),
  ('allowed_exts',      'jpg,jpeg,png,gif,webp,bmp,svg,mp4,webm,mov,mp3,wav,flac,aac,ogg,m4a,pdf,txt,md,csv,json,xml,yml,yaml,log,doc,docx,xls,xlsx,ppt,pptx,zip,rar,7z,tar,gz', '允许上传的扩展名(逗号分隔)'),
  ('recycle_keep_days', '30',                                          '回收站保留天数'),
  ('default_quota_gb',  '50',                                          '新用户默认配额(GB)');
