-- ============================================================
-- Rabbit 网盘 · V2：存储配额策略 + 注册口令码
-- 1) 系统总存储空间 50GB（所有用户共享上限）
-- 2) 用户默认配额降为 5GB
-- 3) 注册口令码表（与 12 位短链同款加密算法）
-- ============================================================

-- ---------- 配置：系统总空间 50GB，默认配额 5GB ----------
INSERT INTO `sys_config` (`config_key`, `config_value`, `description`) VALUES
  ('total_storage_gb', '50', '系统总存储空间(GB，全用户共享上限)');

UPDATE `sys_config` SET `config_value` = '5' WHERE `config_key` = 'default_quota_gb';

-- ---------- 注册口令码表 ----------
CREATE TABLE `invite_code` (
  `id`             bigint      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `code_id`        bigint      NOT NULL COMMENT '雪花算法生成的口令码唯一ID',
  `code`           varchar(12) NOT NULL COMMENT '12位口令码（雪花+盐值混淆+Base62，与短链同款算法）',
  `create_user_id` bigint      NOT NULL COMMENT '生成者（管理员）用户ID',
  `used_by`        bigint      DEFAULT NULL COMMENT '使用该口令码注册的用户ID',
  `used_time`      datetime    DEFAULT NULL COMMENT '使用时间',
  `status`         tinyint     NOT NULL DEFAULT '1' COMMENT '状态：1 可用 0 已使用/已作废',
  `expire_time`    datetime    DEFAULT NULL COMMENT '过期时间，null 为永久有效',
  `create_time`    datetime    NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_code` (`code`),
  UNIQUE KEY `uk_code_id` (`code_id`),
  KEY `idx_status` (`status`),
  KEY `idx_create_user` (`create_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='注册口令码表';
