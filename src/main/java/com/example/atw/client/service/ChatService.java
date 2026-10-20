package com.example.atw.client.service;

import com.example.atw.client.config.ClientModelProperties;
import com.example.atw.client.dto.ChatChoice;
import com.example.atw.client.dto.ChatMessage;
import com.example.atw.client.dto.ChatRequest;
import com.example.atw.client.dto.ChatResponse;
import com.example.atw.client.dto.ChatUsage;
import com.example.atw.client.dto.RequestStatusResponse;
import com.example.atw.client.dto.StopResponse;
import com.example.atw.client.entity.ClientRequestRecord;
import com.example.atw.client.error.ClientApiException;
import com.example.atw.client.error.ClientCodes;
import com.example.atw.client.provider.ChatStreamHandler;
import com.example.atw.client.provider.DeepSeekClient;
import com.example.atw.client.support.ClientHashes;
import com.example.atw.client.support.ClientTime;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    @Autowired
    private ClientModelProperties properties;

    @Autowired
    private ClientRequestService requestService;

    @Autowired
    private DeepSeekClient deepSeekClient;

    @Autowired
    private ChatCancellationRegistry cancellationRegistry;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    @Qualifier("clientTaskExecutor")
    private Executor executor;

    public ChatOutcome handle(Integer userId, ChatRequest request, String requestId) {
        validate(request, requestId);
        int maxTokens = resolveMaxTokens(request);
        checkContextBudget(request, maxTokens, requestId);

        String hash = hash(request, maxTokens);
        ClientRequestService.Begin begin = requestService.begin(
                userId, requestId, ClientRequestService.OP_CHAT, hash,
                request.getGenerationId(), request.getPurpose());

        if (begin.isReplay()) {
            return replay(begin.getRecord());
        }
        ClientRequestRecord record = begin.getRecord();

        if (!properties.getChat().getSpaceId().equals(request.getExpectedPolicyVersion())) {
            requestService.fail(record, 409, ClientCodes.CONFIG_CHANGED, "服务配置已更新，请重新发起。");
            throw new ClientApiException(409, ClientCodes.CONFIG_CHANGED, "服务配置已更新，请重新发起。", requestId);
        }
        if (cancellationRegistry.isStopped(request.getGenerationId())) {
            requestService.fail(record, 409, ClientCodes.REQUEST_CANCELLED, "该生成组已停止");
            throw new ClientApiException(409, ClientCodes.REQUEST_CANCELLED, "该生成组已停止", requestId);
        }

        if (Boolean.TRUE.equals(request.getStream())) {
            DeepSeekClient.StreamHandle handle;
            try {
                handle = deepSeekClient.openStream(request, request.getGenerationId(), maxTokens);
            } catch (ClientApiException e) {
                requestService.fail(record, e.getStatus(), e.getCode(), e.getMessage());
                throw e.withRequestId(requestId);
            } catch (RuntimeException e) {
                requestService.fail(record, 500, ClientCodes.INTERNAL_ERROR, "生成失败");
                throw new ClientApiException(500, ClientCodes.INTERNAL_ERROR, "生成失败", requestId);
            }
            SseEmitter emitter = new SseEmitter((long) properties.getChat().getStreamTimeoutMs());
            AtomicBoolean aborted = new AtomicBoolean(false);
            emitter.onTimeout(() -> {
                aborted.set(true);
                disconnect(handle);
                requestService.stop(record, null);
                completeQuietly(emitter);
            });
            emitter.onError(ex -> {
                aborted.set(true);
                disconnect(handle);
                requestService.stop(record, null);
            });
            try {
                executor.execute(() -> runStream(handle, request, record, emitter, maxTokens, aborted));
            } catch (RuntimeException e) {
                disconnect(handle);
                requestService.fail(record, 503, ClientCodes.SERVICE_UNAVAILABLE, "服务繁忙，请稍后重试。");
                throw new ClientApiException(503, ClientCodes.SERVICE_UNAVAILABLE,
                        "服务繁忙，请稍后重试。", requestId);
            }
            return ChatOutcome.stream(emitter, properties.getChat().getSpaceId());
        }

        try {
            ChatResponse response = deepSeekClient.complete(request, requestId, maxTokens);
            requestService.complete(record, 200, response);
            return ChatOutcome.json(response, properties.getChat().getSpaceId());
        } catch (ClientApiException e) {
            requestService.fail(record, e.getStatus(), e.getCode(), e.getMessage());
            throw e.withRequestId(requestId);
        } catch (RuntimeException e) {
            requestService.fail(record, 500, ClientCodes.INTERNAL_ERROR, "生成失败");
            throw new ClientApiException(500, ClientCodes.INTERNAL_ERROR, "生成失败", requestId);
        }
    }

    public StopResponse stop(Integer userId, String generationId) {
        if (generationId == null || generationId.trim().isEmpty()) {
            throw ClientApiException.invalid("generationId 不能为空");
        }
        if (generationId.length() > 100) {
            throw ClientApiException.invalid("generationId 最多 100 字符");
        }
        List<ClientRequestRecord> records = requestService.findByGeneration(userId, generationId);
        ClientRequestRecord latestAnswer = null;
        for (ClientRequestRecord record : records) {
            if (ClientRequestService.OP_CHAT.equals(record.getOperation())
                    && "answer".equals(record.getPurpose())
                    && (latestAnswer == null || later(record, latestAnswer))) {
                latestAnswer = record;
            }
        }
        boolean answerCompleted = latestAnswer != null
                && ClientRequestRecord.STATE_COMPLETED.equals(latestAnswer.getState());
        cancellationRegistry.cancel(generationId);
        for (ClientRequestRecord record : records) {
            if (ClientRequestRecord.STATE_RUNNING.equals(record.getState())) {
                requestService.stop(record, null);
            }
        }
        return new StopResponse(true, generationId, answerCompleted ? "completed" : "stopped");
    }

    private boolean later(ClientRequestRecord candidate, ClientRequestRecord current) {
        if (candidate.getId() == null) {
            return false;
        }
        return current.getId() == null || candidate.getId() > current.getId();
    }

    public RequestStatusResponse status(Integer userId, String requestId) {
        ClientRequestRecord record = requestService.get(userId, requestId);
        RequestStatusResponse response = new RequestStatusResponse();
        response.setRequestId(record.getRequestId());
        response.setOperation(record.getOperation());
        response.setGenerationId(record.getGenerationId());
        response.setState(record.getState());
        response.setHttpStatus(record.getHttpStatus());
        response.setError(record.getError());
        response.setCode(record.getCode());
        response.setExpiresAt(ClientTime.iso(record.getExpiresAt()));
        if (record.getResultJson() != null) {
            try {
                response.setResult(objectMapper.readTree(record.getResultJson()));
            } catch (JsonProcessingException e) {
                log.warn("恢复结果解析失败: {}", record.getRequestId(), e);
            }
        }
        if (ClientRequestRecord.STATE_STOPPED.equals(record.getState())) {
            response.setPartialContent(record.getPartialContent());
        }
        return response;
    }

    private void runStream(DeepSeekClient.StreamHandle handle, ChatRequest request,
                           ClientRequestRecord record, SseEmitter emitter,
                           int maxTokens, AtomicBoolean aborted) {
        StringBuilder full = new StringBuilder();
        String requestId = request.getRequestId();

        ChatStreamHandler handler = new ChatStreamHandler() {
            @Override
            public void onDelta(String content) {
                if (aborted.get()) {
                    return;
                }
                full.append(content);
                if (full.length() > properties.getChat().getMaxResponseChars()) {
                    aborted.set(true);
                    disconnect(handle);
                    requestService.fail(record, 503, ClientCodes.UPSTREAM_ERROR, "模型返回内容过长");
                    safeSend(emitter, errorFrame(ClientCodes.UPSTREAM_ERROR, "模型返回内容过长", requestId));
                    completeQuietly(emitter);
                    return;
                }
                if (!safeSend(emitter, deltaFrame(content))) {
                    aborted.set(true);
                    disconnect(handle);
                    requestService.stop(record, full.toString());
                }
            }

            @Override
            public void onFinish(String finishReason, ChatUsage usage) {
                if (aborted.get()) {
                    return;
                }
                ChatResponse response = buildResponse(requestId, full.toString(), finishReason, usage);
                try {
                    requestService.complete(record, 200, response);
                } catch (RuntimeException e) {
                    log.warn("聊天结果保存失败 - requestId={}", requestId, e);
                }
                safeSend(emitter, finishFrame(finishReason, usage));
                safeSend(emitter, "[DONE]");
                completeQuietly(emitter);
            }

            @Override
            public void onError(String code, String message) {
                if (aborted.get()) {
                    return;
                }
                if (ClientCodes.REQUEST_CANCELLED.equals(code)) {
                    requestService.stop(record, full.toString());
                } else {
                    requestService.fail(record, statusFor(code), code, message);
                }
                safeSend(emitter, errorFrame(code, message, requestId));
                completeQuietly(emitter);
            }
        };

        try {
            deepSeekClient.readStream(handle, handler);
        } catch (RuntimeException e) {
            if (!aborted.get()) {
                log.warn("流式生成异常 - requestId={}", requestId, e);
                requestService.fail(record, 500, ClientCodes.INTERNAL_ERROR, "生成失败");
                safeSend(emitter, errorFrame(ClientCodes.INTERNAL_ERROR, "生成失败", requestId));
                completeQuietly(emitter);
            }
        }
    }

    private ChatOutcome replay(ClientRequestRecord record) {
        if (ClientRequestRecord.STATE_COMPLETED.equals(record.getState())) {
            JsonNode result = null;
            if (record.getResultJson() != null) {
                try {
                    result = objectMapper.readTree(record.getResultJson());
                } catch (JsonProcessingException e) {
                    throw new ClientApiException(500, ClientCodes.INTERNAL_ERROR, "恢复结果解析失败");
                }
            }
            int status = record.getHttpStatus() == null ? 200 : record.getHttpStatus();
            return ChatOutcome.replay(status, result, properties.getChat().getSpaceId());
        }
        if (ClientRequestRecord.STATE_FAILED.equals(record.getState())) {
            int status = record.getHttpStatus() == null ? 503 : record.getHttpStatus();
            String code = record.getCode() == null ? ClientCodes.UPSTREAM_ERROR : record.getCode();
            String error = record.getError() == null ? "模型调用失败" : record.getError();
            throw new ClientApiException(status, code, error, record.getRequestId());
        }
        throw new ClientApiException(409, ClientCodes.REQUEST_CANCELLED, "该请求已停止", record.getRequestId());
    }

    private void validate(ChatRequest request, String requestId) {
        if (request == null) {
            throw ClientApiException.invalid("请求体不能为空");
        }
        request.rejectOverrides(requestId);
        if (isBlank(request.getGenerationId())) {
            throw ClientApiException.invalid("generationId 不能为空");
        }
        if (request.getGenerationId().length() > 100) {
            throw ClientApiException.invalid("generationId 最多 100 字符");
        }
        String purpose = request.getPurpose();
        if (!"answer".equals(purpose) && !"summary".equals(purpose)) {
            throw ClientApiException.invalid("purpose 必须为 answer 或 summary");
        }
        if (request.getStream() == null) {
            throw ClientApiException.invalid("stream 不能为空");
        }
        if ("summary".equals(purpose) && Boolean.TRUE.equals(request.getStream())) {
            throw ClientApiException.invalid("summary 请求必须使用非流式");
        }
        if (isBlank(request.getExpectedPolicyVersion())) {
            throw ClientApiException.invalid("expectedPolicyVersion 不能为空");
        }
        List<ChatMessage> messages = request.getMessages();
        if (messages == null || messages.isEmpty()) {
            throw ClientApiException.invalid("messages 至少 1 条");
        }
        if (messages.size() > properties.getChat().getMaxMessages()) {
            throw ClientApiException.invalid("messages 超过上限");
        }
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if (message == null || isBlank(message.getRole()) || message.getContent() == null
                    || message.getContent().isEmpty()) {
                throw ClientApiException.invalid("messages 的 role/content 不能为空");
            }
            String role = message.getRole();
            if (!"system".equals(role) && !"user".equals(role) && !"assistant".equals(role)) {
                throw ClientApiException.invalid("messages 包含不支持的 role");
            }
            if (i == 0 && !"system".equals(role)) {
                throw ClientApiException.invalid("第一条消息必须为 system");
            }
            if (i > 0 && "system".equals(role)) {
                throw ClientApiException.invalid("system 消息只能出现在第一条");
            }
            if (i == messages.size() - 1 && !"user".equals(role)) {
                throw ClientApiException.invalid("最后一条消息必须为 user");
            }
        }
    }

    private int resolveMaxTokens(ChatRequest request) {
        int upper = properties.getChat().getMaxOutputTokens();
        int lower = Math.min(properties.getChat().getMinOutputTokens(), upper);
        Integer requested = request.getMaxTokens();
        if (requested != null) {
            if (requested <= 0 || requested > upper) {
                throw ClientApiException.invalid("max_tokens 必须在 1-" + upper + " 之间");
            }
            return Math.max(requested, lower);
        }
        if ("summary".equals(request.getPurpose())) {
            return Math.min(768, upper);
        }
        return upper;
    }

    private void checkContextBudget(ChatRequest request, int maxTokens, String requestId) {
        int estimated = estimateTokens(request.getMessages());
        if (estimated + maxTokens > properties.getChat().getContextBudget()) {
            throw new ClientApiException(400, ClientCodes.CONTEXT_TOO_LARGE,
                    "上下文超出模型预算，请缩短对话或摘要", requestId);
        }
    }

    private int estimateTokens(List<ChatMessage> messages) {
        int ascii = 0;
        int nonAscii = 0;
        for (ChatMessage message : messages) {
            String content = message.getContent();
            for (int i = 0; i < content.length(); i++) {
                if (content.charAt(i) <= 127) {
                    ascii++;
                } else {
                    nonAscii++;
                }
            }
        }
        return Math.max(1, nonAscii + (ascii + 3) / 4);
    }

    private String hash(ChatRequest request, int maxTokens) {
        try {
            String messages = objectMapper.writeValueAsString(request.getMessages());
            return ClientHashes.sha256(request.getGenerationId() + "|" + request.getPurpose() + "|"
                    + request.getStream() + "|" + request.getExpectedPolicyVersion() + "|"
                    + maxTokens + "|" + messages);
        } catch (JsonProcessingException e) {
            throw new ClientApiException(500, ClientCodes.INTERNAL_ERROR, "请求参数序列化失败");
        }
    }

    private ChatResponse buildResponse(String requestId, String content, String finishReason, ChatUsage usage) {
        ChatMessage message = new ChatMessage();
        message.setRole("assistant");
        message.setContent(content);

        ChatChoice choice = new ChatChoice();
        choice.setIndex(0);
        choice.setMessage(message);
        choice.setFinishReason(finishReason == null ? "stop" : finishReason);

        ChatResponse response = new ChatResponse();
        response.setRequestId(requestId);
        response.setChoices(Collections.singletonList(choice));
        response.setUsage(usage);
        return response;
    }

    private String deltaFrame(String content) {
        Map<String, Object> delta = new LinkedHashMap<>();
        delta.put("content", content);
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        choice.put("delta", delta);
        choice.put("finish_reason", null);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("choices", Collections.singletonList(choice));
        return write(root);
    }

    private String finishFrame(String finishReason, ChatUsage usage) {
        Map<String, Object> choice = new LinkedHashMap<>();
        choice.put("index", 0);
        choice.put("delta", new LinkedHashMap<String, Object>());
        choice.put("finish_reason", finishReason);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("choices", Collections.singletonList(choice));
        Map<String, Object> usageMap = usageMap(usage);
        if (usageMap != null) {
            root.put("usage", usageMap);
        }
        return write(root);
    }

    private String errorFrame(String code, String message, String requestId) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("error", message);
        root.put("code", code);
        root.put("requestId", requestId);
        return write(root);
    }

    private Map<String, Object> usageMap(ChatUsage usage) {
        if (usage == null) {
            return null;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        if (usage.getPromptTokens() != null) {
            map.put("prompt_tokens", usage.getPromptTokens());
        }
        if (usage.getCompletionTokens() != null) {
            map.put("completion_tokens", usage.getCompletionTokens());
        }
        if (usage.getTotalTokens() != null) {
            map.put("total_tokens", usage.getTotalTokens());
        }
        return map.isEmpty() ? null : map;
    }

    private String write(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new ClientApiException(500, ClientCodes.INTERNAL_ERROR, "响应序列化失败");
        }
    }

    private boolean safeSend(SseEmitter emitter, String data) {
        try {
            emitter.send(SseEmitter.event().data(data));
            return true;
        } catch (IOException | IllegalStateException e) {
            return false;
        }
    }

    private void completeQuietly(SseEmitter emitter) {
        try {
            emitter.complete();
        } catch (Exception ignored) {
        }
    }

    private void disconnect(DeepSeekClient.StreamHandle handle) {
        try {
            if (handle.getConnection() != null) {
                handle.getConnection().disconnect();
            }
        } catch (Exception ignored) {
        }
    }

    private int statusFor(String code) {
        if (ClientCodes.UPSTREAM_TIMEOUT.equals(code)) {
            return 504;
        }
        if (ClientCodes.CONTENT_REJECTED.equals(code)) {
            return 422;
        }
        if (ClientCodes.RATE_LIMITED.equals(code)) {
            return 429;
        }
        return 503;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public static class ChatOutcome {
        private final ChatResponse response;
        private final SseEmitter emitter;
        private final JsonNode replay;
        private final int status;
        private final String configVersion;

        private ChatOutcome(ChatResponse response, SseEmitter emitter, JsonNode replay,
                            int status, String configVersion) {
            this.response = response;
            this.emitter = emitter;
            this.replay = replay;
            this.status = status;
            this.configVersion = configVersion;
        }

        public static ChatOutcome json(ChatResponse response, String configVersion) {
            return new ChatOutcome(response, null, null, 200, configVersion);
        }

        public static ChatOutcome stream(SseEmitter emitter, String configVersion) {
            return new ChatOutcome(null, emitter, null, 200, configVersion);
        }

        public static ChatOutcome replay(int status, JsonNode replay, String configVersion) {
            return new ChatOutcome(null, null, replay, status, configVersion);
        }

        public ChatResponse getResponse() {
            return response;
        }

        public SseEmitter getEmitter() {
            return emitter;
        }

        public JsonNode getReplay() {
            return replay;
        }

        public int getStatus() {
            return status;
        }

        public String getConfigVersion() {
            return configVersion;
        }

        public boolean isStream() {
            return emitter != null;
        }
    }
}
