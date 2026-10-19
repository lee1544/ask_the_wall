package com.example.atw.client.entity;

import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 头像生成任务。状态：queued/running/downloading/complete/error/cancelled。
 */
@Data
@Entity
@Table(name = "t_client_image_job")
public class ClientImageJob implements Serializable {

    public static final String STATE_QUEUED = "queued";
    public static final String STATE_RUNNING = "running";
    public static final String STATE_DOWNLOADING = "downloading";
    public static final String STATE_COMPLETE = "complete";
    public static final String STATE_ERROR = "error";
    public static final String STATE_CANCELLED = "cancelled";

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "request_id", nullable = false, length = 100)
    private String requestId;

    @Column(nullable = false, length = 16)
    private String state;

    @Column(columnDefinition = "TEXT")
    private String prompt;

    @Column(name = "reference_image", columnDefinition = "LONGTEXT")
    private String referenceImage;

    @Column(columnDefinition = "TEXT")
    private String error;

    @Column(length = 64)
    private String code;

    @Column(length = 255)
    private String message;

    @Column(name = "media_id", length = 64)
    private String mediaId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
}
