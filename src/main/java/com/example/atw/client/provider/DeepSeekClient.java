package com.example.atw.client.provider;

import com.example.atw.client.config.ClientModelProperties;
import com.example.atw.client.dto.ChatChoice;
import com.example.atw.client.dto.ChatMessage;
import com.example.atw.client.dto.ChatRequest;
import com.example.atw.client.dto.ChatResponse;
import com.example.atw.client.dto.ChatUsage;
import com.example.atw.client.error.ClientApiException;
import com.example.atw.client.error.ClientCodes;
import com.example.atw.client.service.ChatCancellationRegistry;
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
    private ClientModelProperties properties;

    @Autowired
    private HttpJsonClient http;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ChatCancellationRegistry cancellationRegistry;

    public ChatResponse complete(ChatRequest request, String requestId, int maxTokens) {
        Map<String, Object> body = buildBody(request, false, maxTokens);
        HttpJsonClient.JsonResult result = http.postJson(
                endpoint(), headers(), body,
                properties.getDeepseek().getConnectTimeoutMs(),
                properties.getDeepseek().getReadTimeoutMs());
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
            if (content == null || content.isEmpty()) {
                throw new ClientApiException(503, ClientCodes.UPSTREAM_ERROR, "模型返回为空");
            }

            ChatMessage outMessage = new ChatMessage();
            outMessage.setRole(message.has("role") ? message.get("role").asText() : "assistant");
            outMessage.setContent(content);

            ChatChoice choice = new ChatChoice();
            choice.setIndex(first.has("index") ? first.get("index").asInt() : 0);
            choice.setMessage(outMessage);
            choice.setFinishReason(first.has("finish_reason") && !first.get("finish_reason").isNull()
                    ? first.get("finish_reason").asText() : "stop");

            ChatResponse response = new ChatResponse();
            response.setRequestId(requestId);
            response.setChoices(Collections.singletonList(choice));
            response.setUsage(parseUsage(root.get("usage")));
            return response;
        } catch (ClientApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ClientApiException(503, ClientCodes.UPSTREAM_ERROR, "模型服务返回异常");
        }
    }

    public StreamHandle openStream(ChatRequest request, String generationId, int maxTokens) {
        Map<String, Object> body = buildBody(request, true, maxTokens);
        HttpJsonClient.StreamResult result = http.postStream(
                endpoint(), headers(), body,
                properties.getDeepseek().getConnectTimeoutMs(),
                properties.getDeepseek().getReadTimeoutMs());
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
                JsonNode delta = frame.path("choices").path(0).path("delta").path("content");
                if (delta.isTextual() && !delta.asText().isEmpty()) {
                    handler.onDelta(delta.asText());
                }
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
            handler.onFinish(finishReason, usage);
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

    private Map<String, Object> buildBody(ChatRequest request, boolean stream, int maxTokens) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getDeepseek().getModel());
        body.put("stream", stream);
        List<Map<String, String>> messages = new ArrayList<>();
        for (ChatMessage message : request.getMessages()) {
            Map<String, String> item = new LinkedHashMap<>();
            item.put("role", message.getRole());
            item.put("content", message.getContent());
            messages.add(item);
        }
        body.put("messages", messages);
        body.put("max_tokens", maxTokens);
        return body;
    }

    private Map<String, String> headers() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + properties.getDeepseek().getApiKey());
        return headers;
    }

    private String endpoint() {
        String base = properties.getDeepseek().getBaseUrl();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base + "/chat/completions";
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
