package com.example.atw.client.provider;

import com.example.atw.client.config.ChatModelRuntime;
import com.example.atw.client.dto.ChatMessage;
import com.example.atw.client.dto.ChatToolCall;
import com.example.atw.client.dto.ChatUsage;
import com.example.atw.client.error.ClientApiException;
import com.example.atw.client.error.ClientCodes;
import com.example.atw.client.service.ChatCancellationRegistry;
import com.example.atw.client.service.WebSearchService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DeepSeekClient {

    @Autowired
    private HttpJsonClient http;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ChatCancellationRegistry cancellationRegistry;

    @Autowired
    private WebSearchService webSearchService;

    public Completion complete(List<ChatMessage> messages, int maxTokens,
                               boolean allowWebSearch, ChatModelRuntime runtime) {
        Map<String, Object> body = buildBody(runtime, messages, false, maxTokens, allowWebSearch);
        HttpJsonClient.JsonResult result = http.postJson(
                endpoint(runtime), headers(runtime), body,
                runtime.getConnectTimeoutMs(), runtime.getReadTimeoutMs());
        if (result.getStatus() >= 400) {
            throw mapProviderError(result.getStatus(), result.getBody());
        }
        try {
            JsonNode root = objectMapper.readTree(result.getBody());
            if (root.has("error")) {
                throw mapProviderError(result.getStatus(), result.getBody());
            }
            JsonNode choices = root.get("choices");
            if (choices == null || !choices.isArray() || choices.size() == 0) {
                throw new ClientApiException(503, ClientCodes.UPSTREAM_ERROR, "模型返回为空");
            }
            JsonNode first = choices.get(0);
            JsonNode message = first.get("message");
            String content = message == null || !message.has("content")
                    || message.get("content").isNull()
                    ? null : message.get("content").asText(null);
            List<ChatToolCall> toolCalls = message == null
                    ? Collections.<ChatToolCall>emptyList()
                    : parseToolCalls(message.get("tool_calls"));
            if ((content == null || content.isEmpty()) && toolCalls.isEmpty()) {
                throw new ClientApiException(503, ClientCodes.UPSTREAM_ERROR, "模型返回为空");
            }

            Completion completion = new Completion();
            completion.setContent(content == null ? "" : content);
            completion.setToolCalls(toolCalls);
            completion.setFinishReason(first.has("finish_reason") && !first.get("finish_reason").isNull()
                    ? first.get("finish_reason").asText() : "stop");
            completion.setUsage(parseUsage(root.get("usage")));
            return completion;
        } catch (ClientApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ClientApiException(503, ClientCodes.UPSTREAM_ERROR, "模型服务返回异常");
        }
    }

    public StreamHandle openStream(List<ChatMessage> messages, String generationId,
                                   int maxTokens, boolean allowWebSearch, ChatModelRuntime runtime) {
        Map<String, Object> body = buildBody(runtime, messages, true, maxTokens, allowWebSearch);
        HttpJsonClient.StreamResult result = http.postStream(
                endpoint(runtime), headers(runtime), body,
                runtime.getConnectTimeoutMs(), runtime.getReadTimeoutMs());
        if (!result.isSuccess()) {
            throw mapProviderError(result.getStatus(), result.getErrorBody());
        }
        cancellationRegistry.register(generationId, result.getConnection());
        return new StreamHandle(result, generationId);
    }

    public void readStream(StreamHandle handle, ChatStreamHandler handler) {
        String finishReason = null;
        ChatUsage usage = null;
        boolean sawDone = false;
        Map<Integer, ToolCallBuilder> toolCallBuilders = new LinkedHashMap<>();
        try {
            BufferedReader reader = handle.result.getReader();
            String line;
            while ((line = reader.readLine()) != null) {
                if (cancellationRegistry.isStopped(handle.generationId)) {
                    handler.onError(ClientCodes.REQUEST_CANCELLED, "生成已停止");
                    return;
                }
                if (line.isEmpty() || line.startsWith(":")) {
                    continue;
                }
                if (!line.startsWith("data:")) {
                    continue;
                }
                String payload = line.substring(5).trim();
                if (payload.isEmpty()) {
                    continue;
                }
                if ("[DONE]".equals(payload)) {
                    sawDone = true;
                    break;
                }
                JsonNode frame;
                try {
                    frame = objectMapper.readTree(payload);
                } catch (Exception e) {
                    continue;
                }
                if (frame.has("error")) {
                    handler.onError(ClientCodes.UPSTREAM_ERROR, "模型生成失败");
                    return;
                }
                JsonNode delta = frame.path("choices").path(0).path("delta");
                JsonNode content = delta.path("content");
                if (content.isTextual() && !content.asText().isEmpty()) {
                    handler.onDelta(content.asText());
                }
                collectToolCalls(delta.path("tool_calls"), toolCallBuilders);
                JsonNode reason = frame.path("choices").path(0).path("finish_reason");
                if (!reason.isMissingNode() && !reason.isNull()) {
                    finishReason = reason.asText();
                }
                if (frame.has("usage")) {
                    usage = parseUsage(frame.get("usage"));
                }
            }

            if (cancellationRegistry.isStopped(handle.generationId)) {
                handler.onError(ClientCodes.REQUEST_CANCELLED, "生成已停止");
                return;
            }
            if (!sawDone || finishReason == null) {
                handler.onError(ClientCodes.UPSTREAM_ERROR, "模型流未正常结束");
                return;
            }
            handler.onFinish(finishReason, usage, buildToolCalls(toolCallBuilders));
        } catch (ClientApiException e) {
            if (cancellationRegistry.isStopped(handle.generationId)) {
                handler.onError(ClientCodes.REQUEST_CANCELLED, "生成已停止");
            } else {
                handler.onError(e.getCode(), e.getMessage());
            }
        } catch (IOException e) {
            if (cancellationRegistry.isStopped(handle.generationId)) {
                handler.onError(ClientCodes.REQUEST_CANCELLED, "生成已停止");
            } else {
                handler.onError(ClientCodes.UPSTREAM_ERROR, "模型流读取失败");
            }
        } finally {
            handle.close();
        }
    }

    private Map<String, Object> buildBody(ChatModelRuntime runtime, List<ChatMessage> messages,
                                          boolean stream, int maxTokens, boolean allowWebSearch) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", runtime.getModel());
        body.put("stream", stream);
        List<Map<String, Object>> providerMessages = new ArrayList<>();
        for (ChatMessage message : messages) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("role", message.getRole());
            if (message.getContent() != null) {
                item.put("content", message.getContent());
            } else if (!"assistant".equals(message.getRole())) {
                item.put("content", "");
            } else {
                item.put("content", null);
            }
            if (message.getToolCallId() != null) {
                item.put("tool_call_id", message.getToolCallId());
            }
            if (message.getToolCalls() != null && !message.getToolCalls().isEmpty()) {
                item.put("tool_calls", message.getToolCalls());
            }
            providerMessages.add(item);
        }
        body.put("messages", providerMessages);
        body.put("max_tokens", maxTokens);
        if (allowWebSearch && webSearchService.isAvailable(runtime)) {
            if (runtime.usesQwenWebSearch()) {
                body.put("enable_search", true);
            } else if (runtime.usesDeepSeekWebSearch()) {
                body.put("tools", webSearchService.definitions());
                body.put("tool_choice", "auto");
            }
        }
        return body;
    }

    private void collectToolCalls(JsonNode nodes, Map<Integer, ToolCallBuilder> builders) {
        if (nodes == null || !nodes.isArray()) {
            return;
        }
        for (JsonNode node : nodes) {
            int index = node.has("index") ? node.get("index").asInt(builders.size()) : builders.size();
            ToolCallBuilder builder = builders.get(index);
            if (builder == null) {
                builder = new ToolCallBuilder();
                builders.put(index, builder);
            }
            if (node.has("id") && !node.get("id").isNull()) {
                builder.id = node.get("id").asText();
            }
            if (node.has("type") && !node.get("type").isNull()) {
                builder.type = node.get("type").asText();
            }
            JsonNode function = node.path("function");
            if (function.has("name") && !function.get("name").isNull()) {
                builder.name.append(function.get("name").asText());
            }
            if (function.has("arguments") && !function.get("arguments").isNull()) {
                builder.arguments.append(function.get("arguments").asText());
            }
        }
    }

    private List<ChatToolCall> buildToolCalls(Map<Integer, ToolCallBuilder> builders) {
        List<ChatToolCall> toolCalls = new ArrayList<>();
        for (ToolCallBuilder builder : builders.values()) {
            if (builder.name.length() == 0) {
                continue;
            }
            ChatToolCall toolCall = new ChatToolCall();
            toolCall.setId(builder.id == null || builder.id.isEmpty()
                    ? "call_" + toolCalls.size() : builder.id);
            toolCall.setType(builder.type == null || builder.type.isEmpty() ? "function" : builder.type);
            ChatToolCall.Function function = new ChatToolCall.Function();
            function.setName(builder.name.toString());
            function.setArguments(builder.arguments.length() == 0 ? "{}" : builder.arguments.toString());
            toolCall.setFunction(function);
            toolCalls.add(toolCall);
        }
        return toolCalls;
    }

    private List<ChatToolCall> parseToolCalls(JsonNode nodes) {
        if (nodes == null || !nodes.isArray()) {
            return Collections.emptyList();
        }
        List<ChatToolCall> toolCalls = new ArrayList<>();
        for (JsonNode node : nodes) {
            JsonNode function = node.path("function");
            String name = function.path("name").asText("");
            if (name.isEmpty()) {
                continue;
            }
            ChatToolCall toolCall = new ChatToolCall();
            toolCall.setId(node.path("id").asText("call_" + toolCalls.size()));
            toolCall.setType(node.path("type").asText("function"));
            ChatToolCall.Function callFunction = new ChatToolCall.Function();
            callFunction.setName(name);
            callFunction.setArguments(function.path("arguments").asText("{}"));
            toolCall.setFunction(callFunction);
            toolCalls.add(toolCall);
        }
        return toolCalls;
    }

    private Map<String, String> headers(ChatModelRuntime runtime) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + runtime.getApiKey());
        return headers;
    }

    private String endpoint(ChatModelRuntime runtime) {
        String base = runtime.getBaseUrl();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + runtime.getChatPath();
    }

    private ChatUsage parseUsage(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        ChatUsage usage = new ChatUsage();
        usage.setPromptTokens(node.has("prompt_tokens") ? node.get("prompt_tokens").asInt() : null);
        usage.setCompletionTokens(node.has("completion_tokens") ? node.get("completion_tokens").asInt() : null);
        usage.setTotalTokens(node.has("total_tokens") ? node.get("total_tokens").asInt() : null);
        return usage;
    }

    private ClientApiException mapProviderError(int status, String body) {
        String message = providerMessage(body);
        String lower = message == null ? "" : message.toLowerCase();
        if (status == 429) {
            return new ClientApiException(429, ClientCodes.RATE_LIMITED, "请求过于频繁，请稍后重试。");
        }
        if (status == 401 || status == 403) {
            return new ClientApiException(503, ClientCodes.SERVICE_UNAVAILABLE, "模型服务暂不可用");
        }
        if (status == 400 && (lower.contains("content") || lower.contains("safety")
                || lower.contains("moderation") || lower.contains("审核"))) {
            return new ClientApiException(422, ClientCodes.CONTENT_REJECTED, "内容未通过审核。");
        }
        return new ClientApiException(503, ClientCodes.SERVICE_UNAVAILABLE, "模型服务暂不可用");
    }

    private String providerMessage(String body) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode error = root.get("error");
            if (error != null) {
                if (error.isTextual()) {
                    return error.asText();
                }
                if (error.has("message")) {
                    return error.get("message").asText();
                }
            }
            if (root.has("message")) {
                return root.get("message").asText();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private static class ToolCallBuilder {
        private String id;
        private String type;
        private final StringBuilder name = new StringBuilder();
        private final StringBuilder arguments = new StringBuilder();
    }

    public static class Completion {
        private String content;
        private String finishReason;
        private ChatUsage usage;
        private List<ChatToolCall> toolCalls;

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public String getFinishReason() {
            return finishReason;
        }

        public void setFinishReason(String finishReason) {
            this.finishReason = finishReason;
        }

        public ChatUsage getUsage() {
            return usage;
        }

        public void setUsage(ChatUsage usage) {
            this.usage = usage;
        }

        public List<ChatToolCall> getToolCalls() {
            return toolCalls;
        }

        public void setToolCalls(List<ChatToolCall> toolCalls) {
            this.toolCalls = toolCalls;
        }
    }

    public class StreamHandle implements Closeable {
        private final HttpJsonClient.StreamResult result;
        private final String generationId;

        private StreamHandle(HttpJsonClient.StreamResult result, String generationId) {
            this.result = result;
            this.generationId = generationId;
        }

        public String getGenerationId() {
            return generationId;
        }

        public HttpURLConnection getConnection() {
            return result.getConnection();
        }

        @Override
        public void close() {
            cancellationRegistry.unregister(generationId, result.getConnection());
            result.close();
        }
    }
}
