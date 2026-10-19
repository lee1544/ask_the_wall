-- =============================================================
-- License 授权功能 - 已有数据库增量升级脚本
-- 在已初始化过的库上执行一次即可（可重复执行）
-- =============================================================

-- 1. 新增激活码表
CREATE TABLE IF NOT EXISTS `t_activation_code`
(
    `id`           INT          NOT NULL AUTO_INCREMENT,
    `code`         VARCHAR(40)  NOT NULL UNIQUE COMMENT '激活码',
    `status`       TINYINT      NOT NULL DEFAULT 0 COMMENT '状态：0-未使用，1-已激活，2-已禁用',
    `batch`        VARCHAR(64) COMMENT '批次标识',
    `licensee`     VARCHAR(100) COMMENT '被授权方名称',
    `edition`      VARCHAR(50) COMMENT '版本/授权类型',
    `valid_days`   INT COMMENT 'License 有效天数，NULL/<=0 表示永久',
    `deadline_at`  DATETIME COMMENT '激活码使用截止时间，NULL 表示不限',
    `machine_code` VARCHAR(100) COMMENT '绑定的机器码',
    `license_id`   VARCHAR(64) COMMENT '签发的 License 编号',
    `license_key`  TEXT COMMENT '签发的 License 授权码',
    `activated_at` DATETIME COMMENT '激活时间',
    `remark`       VARCHAR(255) COMMENT '备注',
    `create_time`  DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time`  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_code` (`code`),
    INDEX `idx_status` (`status`),
    INDEX `idx_batch` (`batch`),
    INDEX `idx_license_id` (`license_id`),
    INDEX `idx_machine_code` (`machine_code`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='激活码表';

-- 2. 新增激活码管理权限
INSERT IGNORE INTO `t_permission` (`permission_code`, `permission_name`, `status`)
VALUES ('license:create-code', '生成/管理激活码', 1),
       ('license:view-code', '查看激活码', 1);

-- 3. 授予超级管理员角色上述权限
INSERT IGNORE INTO `t_role_permission` (`role_id`, `permission_id`)
SELECT r.id, p.id
FROM `t_role` r, `t_permission` p
WHERE r.`role_code` = 'SUPER_ADMIN'
  AND p.`permission_code` IN ('license:create-code', 'license:view-code');
