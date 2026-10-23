package com.example.atw.client.entity;

import com.example.atw.client.config.WebSearchMode;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "t_chat_model_config")
public class ChatModelConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_name", nullable = false, length = 100)
    private String configName;

    @Column(name = "base_url", nullable = false, length = 255)
    private String baseUrl;

    @Column(name = "model_name", nullable = false, length = 120)
    private String modelName;

    @Column(name = "api_key_env", nullable = false, length = 100)
    private String apiKeyEnv;

    @Column(name = "chat_path", nullable = false, length = 120)
    private String chatPath;

    @Column(name = "context_budget", nullable = false)
    private Integer contextBudget;

    @Column(name = "max_output_tokens", nullable = false)
    private Integer maxOutputTokens;

    @Column(name = "connect_timeout_ms", nullable = false)
    private Integer connectTimeoutMs;

    @Column(name = "read_timeout_ms", nullable = false)
    private Integer readTimeoutMs;

    @Column(name = "web_search_enabled", nullable = false)
    private Boolean webSearchEnabled;

    @Enumerated(EnumType.STRING)
    @Column(name = "web_search_mode", nullable = false, length = 32)
    private WebSearchMode webSearchMode;

    @Column(name = "active", nullable = false)
    private Boolean active;

    @Column(name = "revision", nullable = false)
    private Integer revision;

    @Column(name = "remark", length = 255)
    private String remark;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "create_time")
    private LocalDateTime createTime;

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    @Transient
    private Boolean apiKeyConfigured;

    @Transient
    private String policyVersion;
}
