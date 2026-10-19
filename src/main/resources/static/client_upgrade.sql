-- =============================================================
-- 客户端模型接口（/api/v1/client）建表脚本
-- 可重复执行；已存在表不会修改。首次部署请在 system_init.sql 之后执行。
-- =============================================================

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
