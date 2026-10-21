-- =============================================================
-- Ask The Wall - 数据库初始化脚本（唯一部署脚本）
-- 包含：组织、用户、角色、权限、激活码（License）、客户端模型接口表
-- 可重复执行；部署时只需执行本文件一次
-- 已有旧库同样执行本文件即可补齐缺失的表与初始数据
-- =============================================================

SET NAMES utf8mb4;

-- =============================================================
-- 一、用户权限模块
-- =============================================================

-- 1. 组织表
CREATE TABLE IF NOT EXISTS `t_organization`
(
    `id`          INT          NOT NULL AUTO_INCREMENT,
    `org_code`    VARCHAR(50)  NOT NULL UNIQUE COMMENT '组织编码',
    `org_name`    VARCHAR(100) NOT NULL UNIQUE COMMENT '组织名称',
    `description` TEXT COMMENT '组织描述',
    `status`      TINYINT  DEFAULT 1 COMMENT '状态：1-启用，0-禁用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_org_code` (`org_code`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='组织表';

-- 2. 用户表
CREATE TABLE IF NOT EXISTS `t_user`
(
    `id`              INT          NOT NULL AUTO_INCREMENT,
    `username`        VARCHAR(50)  NOT NULL UNIQUE COMMENT '用户名',
    `password`        VARCHAR(255) NOT NULL COMMENT '密码（加密）',
    `real_name`       VARCHAR(50) COMMENT '真实姓名',
    `org_id`          INT          NOT NULL COMMENT '所属组织ID',
    `status`          TINYINT  DEFAULT 1 COMMENT '状态：1-启用，0-禁用',
    `last_login_time` DATETIME COMMENT '最后登录时间',
    `create_time`     DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time`     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_username` (`username`),
    INDEX `idx_org_id` (`org_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户表';

-- 3. 角色表
CREATE TABLE IF NOT EXISTS `t_role`
(
    `id`          INT         NOT NULL AUTO_INCREMENT,
    `role_code`   VARCHAR(50) NOT NULL COMMENT '角色编码',
    `role_name`   VARCHAR(50) NOT NULL COMMENT '角色名称',
    `description` TEXT COMMENT '角色描述',
    `org_id`      INT COMMENT '所属组织ID，NULL表示全局角色',
    `status`      TINYINT  DEFAULT 1 COMMENT '状态：1-启用，0-禁用',
    `create_time` DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time` DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code_org` (`role_code`, `org_id`),
    INDEX `idx_org_id` (`org_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='角色表';

-- 4. 权限表
CREATE TABLE IF NOT EXISTS `t_permission`
(
    `id`              INT          NOT NULL AUTO_INCREMENT,
    `permission_code` VARCHAR(100) NOT NULL UNIQUE COMMENT '权限编码',
    `permission_name` VARCHAR(100) NOT NULL COMMENT '权限名称',
    `status`          TINYINT  DEFAULT 1 COMMENT '状态：1-启用，0-禁用',
    `create_time`     DATETIME DEFAULT CURRENT_TIMESTAMP,
    `update_time`     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    INDEX `idx_permission_code` (`permission_code`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='权限表';

-- 5. 用户角色关联表
CREATE TABLE IF NOT EXISTS `t_user_role`
(
    `id`      INT NOT NULL AUTO_INCREMENT,
    `user_id` INT NOT NULL COMMENT '用户ID',
    `role_id` INT NOT NULL COMMENT '角色ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_role_id` (`role_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='用户角色关联表';

-- 6. 角色权限关联表
CREATE TABLE IF NOT EXISTS `t_role_permission`
(
    `id`            INT NOT NULL AUTO_INCREMENT,
    `role_id`       INT NOT NULL COMMENT '角色ID',
    `permission_id` INT NOT NULL COMMENT '权限ID',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`),
    INDEX `idx_role_id` (`role_id`),
    INDEX `idx_permission_id` (`permission_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='角色权限关联表';

-- =============================================================
-- 二、License 激活码模块
-- =============================================================

-- 7. 激活码表
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

-- =============================================================
-- 三、客户端模型接口模块（/api/v1/client）
-- =============================================================

-- 8. 客户端模型调用幂等与恢复记录
CREATE TABLE IF NOT EXISTS `t_client_request`
(
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`         INT          NOT NULL,
    `request_id`      VARCHAR(100) NOT NULL,
    `operation`       VARCHAR(32)  NOT NULL,
    `request_hash`    VARCHAR(64)           DEFAULT NULL,
    `generation_id`   VARCHAR(100)          DEFAULT NULL,
    `purpose`         VARCHAR(16)           DEFAULT NULL,
    `state`           VARCHAR(16)  NOT NULL,
    `http_status`     INT                   DEFAULT NULL,
    `result_json`     LONGTEXT,
    `error`           TEXT,
    `code`            VARCHAR(64)           DEFAULT NULL,
    `partial_content` LONGTEXT,
    `created_at`      DATETIME     NOT NULL,
    `updated_at`      DATETIME              DEFAULT NULL,
    `expires_at`      DATETIME              DEFAULT NULL,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_client_request_user_req` (`user_id`, `request_id`),
    KEY `idx_client_request_generation` (`user_id`, `generation_id`),
    KEY `idx_client_request_expires` (`expires_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='客户端模型调用幂等与恢复记录';

-- 9. 客户端短期音频/图片资源
CREATE TABLE IF NOT EXISTS `t_client_media`
(
    `id`           VARCHAR(64)  NOT NULL,
    `user_id`      INT          NOT NULL,
    `type`         VARCHAR(16)  NOT NULL,
    `content_type` VARCHAR(64)  NOT NULL,
    `path`         VARCHAR(512) NOT NULL,
    `size`         BIGINT       NOT NULL,
    `cache_key`    VARCHAR(128)          DEFAULT NULL,
    `created_at`   DATETIME     NOT NULL,
    `expires_at`   DATETIME     NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_client_media_user` (`user_id`),
    KEY `idx_client_media_cache` (`user_id`, `cache_key`),
    KEY `idx_client_media_expires` (`expires_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='客户端短期音频/图片资源';

-- 10. 客户端头像生成任务
CREATE TABLE IF NOT EXISTS `t_client_image_job`
(
    `id`              VARCHAR(64)  NOT NULL,
    `user_id`         INT          NOT NULL,
    `request_id`      VARCHAR(100) NOT NULL,
    `state`           VARCHAR(16)  NOT NULL,
    `prompt`          TEXT,
    `reference_image` LONGTEXT,
    `error`           TEXT,
    `code`            VARCHAR(64)           DEFAULT NULL,
    `message`         VARCHAR(255)          DEFAULT NULL,
    `media_id`        VARCHAR(64)           DEFAULT NULL,
    `created_at`      DATETIME     NOT NULL,
    `updated_at`      DATETIME              DEFAULT NULL,
    `expires_at`      DATETIME              DEFAULT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_client_image_job_user` (`user_id`),
    KEY `idx_client_image_job_request` (`user_id`, `request_id`),
    KEY `idx_client_image_job_expires` (`expires_at`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4 COMMENT ='客户端头像生成任务';

-- =============================================================
-- 四、初始数据（幂等，可重复执行）
-- =============================================================

-- 11. 初始权限
INSERT IGNORE INTO `t_permission` (`permission_code`, `permission_name`, `status`)
VALUES ('user:view', '查看用户', 1),
       ('user:create', '创建用户', 1),
       ('user:update', '更新用户', 1),
       ('user:delete', '删除用户', 1),
       ('user:assign-role', '分配角色', 1),
       ('user:reset-password', '重置密码', 1),
       ('org:view', '查看组织', 1),
       ('org:create', '创建组织', 1),
       ('org:update', '更新组织', 1),
       ('org:delete', '删除组织', 1),
       ('role:view', '查看角色', 1),
       ('role:create', '创建角色', 1),
       ('role:update', '更新角色', 1),
       ('role:delete', '删除角色', 1),
       ('role:assign-permission', '分配权限', 1),
       ('license:create-code', '生成/管理激活码', 1),
       ('license:view-code', '查看激活码', 1);

-- 12. 默认组织
INSERT IGNORE INTO `t_organization` (`org_code`, `org_name`, `description`, `status`)
VALUES ('DEFAULT_ORG', '默认组织', '系统默认组织', 1);

-- 13. 全局超级管理员角色（org_id 为空表示全局角色）
-- 注意：MySQL 唯一索引中 NULL 不相等，这里用 NOT EXISTS 防止重复插入
INSERT INTO `t_role` (`role_code`, `role_name`, `description`, `org_id`, `status`)
SELECT 'SUPER_ADMIN', '超级管理员', '系统全局超级管理员，可创建组织和分配管理员', NULL, 1
FROM DUAL
WHERE NOT EXISTS (SELECT 1
                  FROM `t_role`
                  WHERE `role_code` = 'SUPER_ADMIN'
                    AND `org_id` IS NULL);

-- 14. 为超级管理员角色分配所有启用权限
INSERT IGNORE INTO `t_role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `t_role` r
         JOIN `t_permission` p ON p.`status` = 1
WHERE r.`role_code` = 'SUPER_ADMIN'
  AND r.`org_id` IS NULL;

-- 15. 默认超级管理员用户（密码：admin123，BCrypt 加密）
INSERT IGNORE INTO `t_user` (`username`, `password`, `real_name`, `org_id`, `status`)
SELECT 'admin', '$2a$12$ovaL7JxZbkPDkqJdzTjvU.TMD3GRw3IXy3pfGfxrYx8vG23NqlgiK', '超级管理员', o.`id`, 1
FROM `t_organization` o
WHERE o.`org_code` = 'DEFAULT_ORG';

-- 16. 为默认管理员分配超级管理员角色
INSERT IGNORE INTO `t_user_role` (`user_id`, `role_id`)
SELECT u.`id`, r.`id`
FROM `t_user` u
         JOIN `t_role` r
              ON r.`role_code` = 'SUPER_ADMIN' AND r.`org_id` IS NULL
WHERE u.`username` = 'admin';
