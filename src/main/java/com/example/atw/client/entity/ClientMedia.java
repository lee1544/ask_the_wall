package com.example.atw.client.entity;

import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 短期媒体资源（音频/图片），按用户隔离，过期后清理。
 */
@Data
@Entity
@Table(name = "t_client_media")
public class ClientMedia implements Serializable {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(nullable = false, length = 16)
    private String type;

    @Column(name = "content_type", nullable = false, length = 64)
    private String contentType;

    @Column(nullable = false, length = 512)
    private String path;

    @Column(nullable = false)
    private Long size;

    @Column(name = "cache_key", length = 128)
    private String cacheKey;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;
}
