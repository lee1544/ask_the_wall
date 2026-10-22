package com.example.atw.client.service;

import com.example.atw.client.config.ClientModelProperties;
import com.example.atw.client.dto.ChatChoice;
import com.example.atw.client.dto.ChatMessage;
import com.example.atw.client.dto.ChatRequest;
import com.example.atw.client.dto.ChatResponse;
import com.example.atw.client.dto.ChatToolCall;
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
import java.util.concurrent.atomic.AtomicReference;

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
    private WebSearchService webSearchService;

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

        List<ChatMessage> conversation = copyMessages(request.getMessages());
        if (Boolean.TRUE.equals(request.getStream())) {
            DeepSeekClient.StreamHandle handle;
            try {
                handle = deepSeekClient.openStream(conversation, request.getGenerationId(),
                        maxTokens, allowTools(request));
            } catch (ClientApiException e) {
                requestService.fail(record, e.getStatus(), e.getCode(), e.getMessage());
                throw e.withRequestId(requestId);
            } catch (RuntimeException e) {
                requestService.fail(record, 500, ClientCodes.INTERNAL_ERROR, "生成失败");
                throw new ClientApiException(500, ClientCodes.INTERNAL_ERROR, "生成失败", requestId);
            }
            SseEmitter emitter = new SseEmitter((long) properties.getChat().getStreamTimeoutMs());
            AtomicBoolean aborted = new AtomicBoolean(false);
            AtomicReference<DeepSeekClient.StreamHandle> currentHandle =
                    new AtomicReference<>(handle);
            emitter.onTimeout(() -> {
                aborted.set(true);
                disconnect(currentHandle.get());
                requestService.stop(record, null);
                completeQuietly(emitter);
            });
            emitter.onError(ex -> {
                aborted.set(true);
                disconnect(currentHandle.get());
                requestService.stop(record, null);
            });
            try {
                executor.execute(() -> runStream(handle, currentHandle, conversation, request,
                        record, emitter, maxTokens, aborted));
            } catch (RuntimeException e) {
                disconnect(handle);
                requestService.fail(record, 503, ClientCodes.SERVICE_UNAVAILABLE, "服务繁忙，请稍后重试。");
                throw new ClientApiException(503, ClientCodes.SERVICE_UNAVAILABLE,
                        "服务繁忙，请稍后重试。", requestId);
            }
            return ChatOutcome.stream(emitter, properties.getChat().getSpaceId());
        }

        try {
            ChatResponse response = completeWithTools(conversation, requestId, maxTokens,
                    allowTools(request));
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

    private void runStream(DeepSeekClient.StreamHandle firstHandle,
                           AtomicReference<DeepSeekClient.StreamHandle> currentHandle,
                           List<ChatMessage> messages, ChatRequest request,
                           ClientRequestRecord record, SseEmitter emitter,
                           int maxTokens, AtomicBoolean aborted) {
        String requestId = request.getRequestId();
        StringBuilder totalContent = new StringBuilder();
        ChatUsage totalUsage = null;
        boolean allowTools = allowTools(request);
        int rounds = 0;
        DeepSeekClient.StreamHandle handle = firstHandle;
        try {
            while (true) {
                StreamRound round = readRound(handle, record, emitter, requestId, aborted, totalContent);
                if (aborted.get()) {
                    return;
                }
                if (round.errorCode != null) {
                    if (ClientCodes.REQUEST_CANCELLED.equals(round.errorCode)) {
                        requestService.stop(record, totalContent.toString());
                    } else {
                        requestService.fail(record, statusFor(round.errorCode), round.errorCode,
                                round.errorMessage);
                    }
                    safeSend(emitter, errorFrame(round.errorCode, round.errorMessage, requestId));
                    completeQuietly(emitter);
                    return;
                }
                totalUsage = addUsage(totalUsage, round.usage);

                List<ChatToolCall> toolCalls = round.toolCalls;
                if (toolCalls == null || toolCalls.isEmpty()) {
                    if (round.content.toString().trim().isEmpty()) {
                        requestService.fail(record, 503, ClientCodes.UPSTREAM_ERROR, "模型返回为空");
                        safeSend(emitter, errorFrame(ClientCodes.UPSTREAM_ERROR, "模型返回为空", requestId));
                        completeQuietly(emitter);
                        return;
                    }
                    ChatResponse response = buildResponse(requestId, totalContent.toString(),
                            round.finishReason, totalUsage);
                    try {
                        requestService.complete(record, 200, response);
                    } catch (RuntimeException e) {
                        log.warn("聊天结果保存失败 - requestId={}", requestId, e);
                    }
                    safeSend(emitter, finishFrame(round.finishReason, totalUsage));
                    safeSend(emitter, "[DONE]");
                    completeQuietly(emitter);
                    return;
                }

                if (!allowTools) {
                    requestService.fail(record, 503, ClientCodes.UPSTREAM_ERROR, "模型重复请求工具");
                    safeSend(emitter, errorFrame(ClientCodes.UPSTREAM_ERROR, "模型重复请求工具", requestId));
                    completeQuietly(emitter);
                    return;
                }

                boolean limitReached = rounds >= properties.getChat().getMaxToolRounds();
                if (limitReached) {
                    appendToolMessages(messages, round.content.toString(), toolCalls, true);
                    allowTools = false;
                } else {
                    rounds++;
                    appendToolMessages(messages, round.content.toString(), toolCalls, false);
                    checkContextBudget(messages, maxTokens, requestId);
                }
                if (aborted.get()) {
                    return;
                }

                handle = deepSeekClient.openStream(messages, request.getGenerationId(),
                        maxTokens, allowTools);
                currentHandle.set(handle);
            }
        } catch (ClientApiException e) {
            if (!aborted.get()) {
                requestService.fail(record, e.getStatus(), e.getCode(), e.getMessage());
                safeSend(emitter, errorFrame(e.getCode(), e.getMessage(), requestId));
                completeQuietly(emitter);
            }
        } catch (RuntimeException e) {
            if (!aborted.get()) {
                log.warn("流式生成异常 - requestId={}", requestId, e);
                requestService.fail(record, 500, ClientCodes.INTERNAL_ERROR, "生成失败");
                safeSend(emitter, errorFrame(ClientCodes.INTERNAL_ERROR, "生成失败", requestId));
                completeQuietly(emitter);
            }
        }
    }

    private StreamRound readRound(DeepSeekClient.StreamHandle handle, ClientRequestRecord record,
                                  SseEmitter emitter, String requestId,
                                  AtomicBoolean aborted, StringBuilder totalContent) {
        StreamRound round = new StreamRound();
        ChatStreamHandler handler = new ChatStreamHandler() {
            @Override
            public void onDelta(String content) {
                if (aborted.get()) {
                    return;
                }
                round.content.append(content);
                totalContent.append(content);
                if (totalContent.length() > properties.getChat().getMaxResponseChars()) {
                    aborted.set(true);
                    disconnect(handle);
                    requestService.fail(record, 503, ClientCodes.UPSTREAM_ERROR, "模型返回内容过长");
                    safeSend(emitter, errorFrame(ClientCodes.UPSTREAM_ERROR,
                            "模型返回内容过长", requestId));
                    completeQuietly(emitter);
                    return;
                }
                if (!safeSend(emitter, deltaFrame(content))) {
                    aborted.set(true);
                    disconnect(handle);
                    requestService.stop(record, totalContent.toString());
                }
            }

            @Override
            public void onFinish(String finishReason, ChatUsage usage, List<ChatToolCall> toolCalls) {
                if (aborted.get()) {
                    return;
                }
                round.finishReason = finishReason;
                round.usage = usage;
                round.toolCalls = toolCalls;
            }

            @Override
            public void onError(String code, String message) {
                if (aborted.get()) {
                    return;
                }
                round.errorCode = code;
                round.errorMessage = message;
            }
        };

        try {
            deepSeekClient.readStream(handle, handler);
        } catch (RuntimeException e) {
            if (!aborted.get()) {
                log.warn("流式生成异常 - requestId={}", requestId, e);
                round.errorCode = ClientCodes.INTERNAL_ERROR;
                round.errorMessage = "生成失败";
            }
        }
        if (!aborted.get() && round.errorCode == null && round.finishReason == null) {
            round.errorCode = ClientCodes.UPSTREAM_ERROR;
            round.errorMessage = "模型流未正常结束";
        }
        return round;
    }

    private ChatResponse completeWithTools(List<ChatMessage> messages, String requestId,
                                           int maxTokens, boolean allowTools) {
        StringBuilder totalContent = new StringBuilder();
        ChatUsage totalUsage = null;
        int rounds = 0;
        while (true) {
            DeepSeekClient.Completion completion = deepSeekClient.complete(messages, maxTokens, allowTools);
            String roundContent = completion.getContent() == null ? "" : completion.getContent();
            totalContent.append(roundContent);
            totalUsage = addUsage(totalUsage, completion.getUsage());

            List<ChatToolCall> toolCalls = completion.getToolCalls();
            if (toolCalls == null || toolCalls.isEmpty()) {
                if (roundContent.trim().isEmpty()) {
                    throw new ClientApiException(503, ClientCodes.UPSTREAM_ERROR, "模型返回为空");
                }
                return buildResponse(requestId, totalContent.toString(),
                        completion.getFinishReason(), totalUsage);
            }
            if (!allowTools) {
                throw new ClientApiException(503, ClientCodes.UPSTREAM_ERROR, "模型重复请求工具");
            }

            boolean limitReached = rounds >= properties.getChat().getMaxToolRounds();
            if (limitReached) {
                appendToolMessages(messages, completion.getContent(), toolCalls, true);
                allowTools = false;
            } else {
                rounds++;
                appendToolMessages(messages, completion.getContent(), toolCalls, false);
                checkContextBudget(messages, maxTokens, requestId);
            }
        }
    }

    private void appendToolMessages(List<ChatMessage> messages, String assistantContent,
                                    List<ChatToolCall> toolCalls, boolean limitReached) {
        ChatMessage assistant = new ChatMessage();
        assistant.setRole("assistant");
        assistant.setContent(assistantContent == null || assistantContent.isEmpty()
                ? null : assistantContent);
        assistant.setToolCalls(toolCalls);
        messages.add(assistant);

        int maxCalls = Math.max(1, properties.getWebSearch().getMaxCallsPerRound());
        for (int i = 0; i < toolCalls.size(); i++) {
            ChatToolCall call = toolCalls.get(i);
            String result;
            if (limitReached) {
                result = errorJson("联网搜索轮次已达上限，请直接根据已有信息回答，不要再调用工具。");
            } else if (i >= maxCalls) {
                result = errorJson("单轮工具调用数量超过上限。");
            } else {
                result = executeTool(call);
            }
            ChatMessage tool = new ChatMessage();
            tool.setRole("tool");
            tool.setToolCallId(call.getId() == null ? "call_" + i : call.getId());
            tool.setContent(result);
            messages.add(tool);
        }
    }

    private String executeTool(ChatToolCall call) {
        if (call == null || call.getFunction() == null
                || isBlank(call.getFunction().getName())) {
            return errorJson("工具调用参数无效。");
        }
        return webSearchService.execute(call.getFunction().getName(),
                call.getFunction().getArguments());
    }

    private List<ChatMessage> copyMessages(List<ChatMessage> source) {
        List<ChatMessage> messages = new ArrayList<>();
        for (ChatMessage message : source) {
            ChatMessage copy = new ChatMessage();
            copy.setRole(message.getRole());
            copy.setContent(message.getContent());
            messages.add(copy);
        }
        return messages;
    }

    private ChatUsage addUsage(ChatUsage total, ChatUsage usage) {
        if (usage == null) {
            return total;
        }
        if (total == null) {
            total = new ChatUsage();
        }
        total.setPromptTokens(sum(total.getPromptTokens(), usage.getPromptTokens()));
        total.setCompletionTokens(sum(total.getCompletionTokens(), usage.getCompletionTokens()));
        total.setTotalTokens(sum(total.getTotalTokens(), usage.getTotalTokens()));
        return total;
    }

    private Integer sum(Integer left, Integer right) {
        if (left == null && right == null) {
            return null;
        }
        return (left == null ? 0 : left) + (right == null ? 0 : right);
    }

    private String errorJson(String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("error", message);
        return write(payload);
    }

    private static class StreamRound {
        private final StringBuilder content = new StringBuilder();
        private String finishReason;
        private ChatUsage usage;
        private List<ChatToolCall> toolCalls;
        private String errorCode;
        private String errorMessage;
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

    private boolean allowTools(ChatRequest request) {
        return "answer".equals(request.getPurpose()) && webSearchService.isAvailable();
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
        checkContextBudget(request.getMessages(), maxTokens, requestId);
    }

    private void checkContextBudget(List<ChatMessage> messages, int maxTokens, String requestId) {
        int estimated = estimateTokens(messages);
        if (estimated + maxTokens > properties.getChat().getContextBudget()) {
            throw new ClientApiException(400, ClientCodes.CONTEXT_TOO_LARGE,
                    "上下文超出模型预算，请缩短对话或摘要", requestId);
        }
    }

    private int estimateTokens(List<ChatMessage> messages) {
        int ascii = 0;
        int nonAscii = 0;
        for (ChatMessage message : messages) {
            if (message == null) {
                continue;
            }
            String content = message.getContent();
            if (content != null) {
                for (int i = 0; i < content.length(); i++) {
                    if (content.charAt(i) <= 127) {
                        ascii++;
                    } else {
                        nonAscii++;
                    }
                }
            }
            if (message.getToolCalls() != null) {
                for (ChatToolCall call : message.getToolCalls()) {
                    if (call != null && call.getFunction() != null
                            && call.getFunction().getArguments() != null) {
                        String arguments = call.getFunction().getArguments();
                        for (int i = 0; i < arguments.length(); i++) {
                            if (arguments.charAt(i) <= 127) {
                                ascii++;
                            } else {
                                nonAscii++;
                            }
                        }
                    }
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
        if (handle == null) {
            return;
        }
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
