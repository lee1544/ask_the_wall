package com.example.atw.client.config;

import lombok.Data;
import lombok.ToString;

@Data
public class ChatModelRuntime {

    private Long configId;

    private String name;

    private String baseUrl;

    private String model;

    @ToString.Exclude
    private String apiKey;

    private String apiKeyEnv;

    private String chatPath;

    private int contextBudget;

    private int maxOutputTokens;

    private int connectTimeoutMs;

    private int readTimeoutMs;

    private boolean webSearchEnabled;

    private WebSearchMode webSearchMode = WebSearchMode.DEEPSEEK_TOOLS;

    private boolean fallback;

    private String policyVersion;

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.trim().isEmpty();
    }

    public boolean usesDeepSeekWebSearch() {
        return webSearchMode == null || webSearchMode == WebSearchMode.DEEPSEEK_TOOLS;
    }

    public boolean usesQwenWebSearch() {
        return webSearchMode == WebSearchMode.QWEN_ENABLE_SEARCH;
    }
}
