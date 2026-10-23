package com.example.atw.client.service;

import com.example.atw.client.config.ChatModelRuntime;
import com.example.atw.client.config.ClientModelProperties;
import com.example.atw.client.config.WebSearchMode;
import com.example.atw.client.dto.ChatModelConfigRequest;
import com.example.atw.client.entity.ChatModelConfig;
import com.example.atw.client.error.ClientApiException;
import com.example.atw.client.provider.HttpJsonClient;
import com.example.atw.client.repository.ChatModelConfigRepository;
import com.example.atw.exception.BusinessException;
import com.example.atw.exception.NotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URL;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ChatModelConfigService {

    private static final String DEFAULT_API_KEY_ENV = "DEEPSEEK_API_KEY";

    private static final String BAILIAN_API_KEY_ENV = "BAILIAN_API_KEY";

    @Autowired
    private ChatModelConfigRepository repository;

    @Autowired
    private ClientModelProperties properties;

    @Autowired
    private HttpJsonClient httpJsonClient;

    @Autowired
    private ObjectMapper objectMapper;

    public ChatModelRuntime runtime() {
        Optional<ChatModelConfig> active = repository.findFirstByActiveTrue();
        if (!active.isPresent()) {
            return fallbackRuntime();
        }
        return toRuntime(active.get());
    }

    public String currentPolicyVersion() {
        return runtime().getPolicyVersion();
    }

    public List<ChatModelConfig> list() {
        List<ChatModelConfig> result = new ArrayList<>();
        for (ChatModelConfig config : repository.findAllByOrderByActiveDescUpdateTimeDescIdDesc()) {
            result.add(decorate(config));
        }
        return result;
    }

    public ChatModelConfig get(Long id) {
        ChatModelConfig config = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("模型配置不存在"));
        return decorate(config);
    }

    @Transactional
    public ChatModelConfig create(ChatModelConfigRequest request) {
        String configName = request.getConfigName().trim();
        if (repository.findByConfigName(configName).isPresent()) {
            throw new BusinessException("配置名称已存在");
        }
        ChatModelConfig config = new ChatModelConfig();
        apply(config, request);
        LocalDateTime now = LocalDateTime.now();
        config.setActive(false);
        config.setRevision(1);
        config.setCreateTime(now);
        config.setUpdateTime(now);
        return decorate(repository.save(config));
    }

    @Transactional
    public ChatModelConfig update(Long id, ChatModelConfigRequest request) {
        ChatModelConfig config = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("模型配置不存在"));
        String configName = request.getConfigName().trim();
        Optional<ChatModelConfig> sameName = repository.findByConfigName(configName);
        if (sameName.isPresent() && !sameName.get().getId().equals(id)) {
            throw new BusinessException("配置名称已存在");
        }
        apply(config, request);
        config.setRevision(nextRevision(config.getRevision()));
        config.setUpdateTime(LocalDateTime.now());
        return decorate(repository.save(config));
    }

    @Transactional
    public ChatModelConfig activate(Long id) {
        ChatModelConfig target = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("模型配置不存在"));
        LocalDateTime now = LocalDateTime.now();
        List<ChatModelConfig> all = repository.findAll();
        for (ChatModelConfig config : all) {
            if (Boolean.TRUE.equals(config.getActive())) {
                config.setActive(false);
                config.setUpdateTime(now);
            }
        }
        target.setActive(true);
        target.setRevision(nextRevision(target.getRevision()));
        target.setUpdateTime(now);
        repository.saveAll(all);
        return decorate(target);
    }

    @Transactional
    public void delete(Long id) {
        ChatModelConfig config = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("模型配置不存在"));
        if (Boolean.TRUE.equals(config.getActive())) {
            throw new BusinessException("启用中的模型配置不能删除，请先启用其它配置");
        }
        repository.delete(config);
    }

    public Map<String, Object> testConnection(Long id) {
        ChatModelConfig config = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("模型配置不存在"));
        ChatModelRuntime runtime = toRuntime(config);
        Map<String, Object> result = new LinkedHashMap<>();
        if (!runtime.hasApiKey()) {
            result.put("success", false);
            result.put("message", "环境变量 " + runtime.getApiKeyEnv() + " 未配置");
            return result;
        }

        Map<String, Object> userMessage = new LinkedHashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", "ping");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", runtime.getModel());
        body.put("messages", Collections.singletonList(userMessage));
        body.put("stream", false);
        body.put("max_tokens", 8);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + runtime.getApiKey());
        try {
            HttpJsonClient.JsonResult response = httpJsonClient.postJson(
                    endpoint(runtime.getBaseUrl(), runtime.getChatPath()), headers, body,
                    Math.min(runtime.getConnectTimeoutMs(), 10000),
                    Math.min(runtime.getReadTimeoutMs(), 30000));
            if (response.getStatus() < 200 || response.getStatus() >= 300) {
                result.put("success", false);
                result.put("message", providerError(response.getStatus(), response.getBody()));
                return result;
            }
            JsonNode root = objectMapper.readTree(response.getBody());
            if (root.path("choices").isArray() && root.path("choices").size() > 0) {
                result.put("success", true);
                result.put("message", "连接成功，模型 " + runtime.getModel() + " 已响应");
                return result;
            }
            result.put("success", false);
            result.put("message", "模型返回格式异常，请检查接口地址和模型名称");
            return result;
        } catch (ClientApiException e) {
            result.put("success", false);
            result.put("message", e.getMessage());
            return result;
        } catch (IOException e) {
            result.put("success", false);
            result.put("message", "模型返回内容无法解析");
            return result;
        }
    }

    private ChatModelRuntime fallbackRuntime() {
        ClientModelProperties.Deepseek deepseek = properties.getDeepseek();
        ClientModelProperties.Chat chat = properties.getChat();
        ChatModelRuntime runtime = new ChatModelRuntime();
        runtime.setName("环境默认配置");
        runtime.setBaseUrl(trimTrailingSlash(deepseek.getBaseUrl()));
        runtime.setModel(deepseek.getModel());
        runtime.setApiKeyEnv(DEFAULT_API_KEY_ENV);
        runtime.setChatPath("/chat/completions");
        runtime.setContextBudget(chat.getContextBudget());
        runtime.setMaxOutputTokens(chat.getMaxOutputTokens());
        runtime.setConnectTimeoutMs(deepseek.getConnectTimeoutMs());
        runtime.setReadTimeoutMs(deepseek.getReadTimeoutMs());
        runtime.setWebSearchEnabled(properties.getWebSearch().isEnabled());
        runtime.setWebSearchMode(WebSearchMode.DEEPSEEK_TOOLS);
        runtime.setFallback(true);
        runtime.setPolicyVersion(chat.getSpaceId());
        runtime.setApiKey(resolveApiKey(DEFAULT_API_KEY_ENV));
        return runtime;
    }

    private ChatModelRuntime toRuntime(ChatModelConfig config) {
        ChatModelRuntime runtime = new ChatModelRuntime();
        runtime.setConfigId(config.getId());
        runtime.setName(config.getConfigName());
        runtime.setBaseUrl(trimTrailingSlash(config.getBaseUrl()));
        runtime.setModel(config.getModelName());
        runtime.setApiKeyEnv(config.getApiKeyEnv());
        runtime.setChatPath(normalizePath(config.getChatPath()));
        runtime.setContextBudget(config.getContextBudget());
        runtime.setMaxOutputTokens(config.getMaxOutputTokens());
        runtime.setConnectTimeoutMs(config.getConnectTimeoutMs());
        runtime.setReadTimeoutMs(config.getReadTimeoutMs());
        runtime.setWebSearchEnabled(Boolean.TRUE.equals(config.getWebSearchEnabled()));
        runtime.setWebSearchMode(config.getWebSearchMode() == null
                ? WebSearchMode.DEEPSEEK_TOOLS : config.getWebSearchMode());
        runtime.setFallback(false);
        runtime.setPolicyVersion(policyVersion(config));
        runtime.setApiKey(resolveApiKey(config.getApiKeyEnv()));
        return runtime;
    }

    private ChatModelConfig decorate(ChatModelConfig config) {
        ChatModelRuntime runtime = toRuntime(config);
        config.setApiKeyConfigured(runtime.hasApiKey());
        config.setPolicyVersion(runtime.getPolicyVersion());
        config.setWebSearchMode(runtime.getWebSearchMode());
        return config;
    }

    private void apply(ChatModelConfig config, ChatModelConfigRequest request) {
        String baseUrl = trimTrailingSlash(request.getBaseUrl().trim());
        validateBaseUrl(baseUrl);
        if (request.getContextBudget() < request.getMaxOutputTokens()) {
            throw new BusinessException("上下文预算不能小于最大输出 Token");
        }
        config.setConfigName(request.getConfigName().trim());
        config.setBaseUrl(baseUrl);
        config.setModelName(request.getModelName().trim());
        config.setApiKeyEnv(request.getApiKeyEnv().trim());
        config.setChatPath(normalizePath(request.getChatPath().trim()));
        config.setContextBudget(request.getContextBudget());
        config.setMaxOutputTokens(request.getMaxOutputTokens());
        config.setConnectTimeoutMs(request.getConnectTimeoutMs());
        config.setReadTimeoutMs(request.getReadTimeoutMs());
        config.setWebSearchEnabled(Boolean.TRUE.equals(request.getWebSearchEnabled()));
        config.setWebSearchMode(request.getWebSearchMode() == null
                ? WebSearchMode.DEEPSEEK_TOOLS : request.getWebSearchMode());
        String remark = request.getRemark();
        config.setRemark(remark == null || remark.trim().isEmpty() ? null : remark.trim());
    }

    private void validateBaseUrl(String baseUrl) {
        try {
            URL url = new URL(baseUrl);
            String protocol = url.getProtocol();
            if (!"http".equalsIgnoreCase(protocol) && !"https".equalsIgnoreCase(protocol)) {
                throw new BusinessException("接口地址必须以 http:// 或 https:// 开头");
            }
        } catch (IOException e) {
            throw new BusinessException("接口地址格式不正确");
        }
    }

    private String resolveApiKey(String envName) {
        if (!StringUtils.hasText(envName)) {
            return null;
        }
        String value = environmentVariable(envName.trim());
        if (StringUtils.hasText(value)) {
            return value.trim();
        }
        String fallback = null;
        if (DEFAULT_API_KEY_ENV.equalsIgnoreCase(envName.trim())) {
            fallback = properties.getDeepseek().getApiKey();
        } else if (BAILIAN_API_KEY_ENV.equalsIgnoreCase(envName.trim())) {
            fallback = properties.getBailian().getApiKey();
        }
        return StringUtils.hasText(fallback) ? fallback.trim() : null;
    }

    String environmentVariable(String name) {
        return System.getenv(name);
    }

    private String policyVersion(ChatModelConfig config) {
        int revision = config.getRevision() == null || config.getRevision() < 1
                ? 1 : config.getRevision();
        return "chat-model-" + config.getId() + "-" + revision;
    }

    private int nextRevision(Integer revision) {
        return revision == null || revision < 1 ? 1 : revision + 1;
    }

    private String providerError(int status, String body) {
        String message = null;
        if (StringUtils.hasText(body)) {
            try {
                JsonNode root = objectMapper.readTree(body);
                message = root.path("error").path("message").asText(null);
            } catch (IOException ignored) {
            }
        }
        if (!StringUtils.hasText(message)) {
            message = "HTTP " + status;
        }
        message = message.replaceAll("\\s+", " ").trim();
        if (message.length() > 200) {
            message = message.substring(0, 200);
        }
        return "连接失败：" + message;
    }

    private String endpoint(String baseUrl, String chatPath) {
        return trimTrailingSlash(baseUrl) + normalizePath(chatPath);
    }

    private String normalizePath(String path) {
        if (!StringUtils.hasText(path)) {
            return "/chat/completions";
        }
        String value = path.trim();
        return value.startsWith("/") ? value : "/" + value;
    }

    private String trimTrailingSlash(String value) {
        if (value == null) {
            return "";
        }
        String result = value.trim();
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }
}
