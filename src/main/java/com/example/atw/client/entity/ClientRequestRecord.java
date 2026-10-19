package com.example.atw.client.entity;

import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 模型调用幂等与短期恢复记录。终端状态和结果至少保留 24 小时。
 */
@Data
@Entity
@Table(name = "t_client_request",
        uniqueConstraints = @UniqueConstraint(name = "uk_client_request_user_req", columnNames = {"user_id", "request_id"}))
public class ClientRequestRecord implements Serializable {

    public static final String STATE_RUNNING = "running";
    public static final String STATE_COMPLETED = "completed";
    public static final String STATE_STOPPED = "stopped";
    public static final String STATE_FAILED = "failed";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Integer userId;

    @Column(name = "request_id", nullable = false, length = 100)
    private String requestId;

    @Column(nullable = false, length = 32)
    private String operation;

    @Column(name = "request_hash", length = 64)
    private String requestHash;

    @Column(name = "generation_id", length = 100)
    private String generationId;

    @Column(length = 16)
    private String purpose;

    @Column(nullable = false, length = 16)
    private String state;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Column(name = "result_json", columnDefinition = "LONGTEXT")
    private String resultJson;

    @Column(columnDefinition = "TEXT")
    private String error;

    @Column(length = 64)
    private String code;

    @Column(name = "partial_content", columnDefinition = "LONGTEXT")
    private String partialContent;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "expires_at")
    private LocalDateTime expiresAt;
}
