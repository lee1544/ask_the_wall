package com.example.atw.client.provider;

import com.example.atw.client.config.ClientModelProperties;
import com.example.atw.client.error.ClientApiException;
import com.example.atw.client.error.ClientCodes;
import com.example.atw.client.support.OssUrlValidator;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class BailianClient {

    @Autowired
    private ClientModelProperties properties;

    @Autowired
    private HttpJsonClient http;

    @Autowired
    private ObjectMapper objectMapper;

    public String recognize(byte[] wav) {
        Map<String, Object> inputAudio = new LinkedHashMap<>();
        inputAudio.put("data", "data:audio/wav;base64," + Base64.getEncoder().encodeToString(wav));
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("type", "input_audio");
        content.put("input_audio", inputAudio);
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", Collections.singletonList(content));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getAsr().getModel());
        body.put("messages", Collections.singletonList(message));
        body.put("stream", false);
        body.put("asr_options", Collections.singletonMap("enable_itn", true));

        JsonNode root = post(asrEndpoint(), body, properties.getAsr().getReadTimeoutMs());
        JsonNode node = root.path("choices").path(0).path("message").path("content");
        String text = null;
        if (node.isTextual()) {
            text = node.asText();
        } else if (node.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode part : node) {
                if (part.has("text")) {
                    sb.append(part.get("text").asText());
                }
            }
            text = sb.toString();
        }
        if (text == null || text.trim().isEmpty()) {
            throw new ClientApiException(422, ClientCodes.NO_SPEECH, "没有识别到有效语音");
        }
        text = text.trim();
        if (text.length() > properties.getAsr().getMaxChars()) {
            text = text.substring(0, properties.getAsr().getMaxChars());
        }
        return text;
    }

    public byte[] synthesize(String text) {
        long deadline = System.currentTimeMillis() + properties.getTts().getReadTimeoutMs();
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("text", text);
        input.put("voice", properties.getTts().getVoice());
        input.put("language_type", properties.getTts().getLanguageType());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getTts().getModel());
        body.put("input", input);

        JsonNode root = post(multimodalEndpoint(), body, properties.getTts().getReadTimeoutMs());
        String url = root.path("output").path("audio").path("url").asText(null);
        String secureUrl = OssUrlValidator.toSecureUrl(url);
        HttpJsonClient.DownloadResult result = http.download(
                secureUrl,
                properties.getTts().getMaxAudioBytes(),
                properties.getBailian().getConnectTimeoutMs(),
                remainingMs(deadline));
        if (result.getStatus() != 200 || result.getBody() == null) {
            throw upstream("语音合成结果下载失败");
        }
        return result.getBody();
    }

    public byte[] generateImage(String text, byte[] jpeg) {
        String secureUrl = requestImage(text, jpeg);
        return downloadOss(secureUrl, properties.getImage().getMaxResultBytes(),
                properties.getImage().getDownloadTimeoutMs());
    }

    public String requestImage(String text, byte[] jpeg) {
        List<Map<String, Object>> content = new ArrayList<>();
        if (jpeg != null) {
            Map<String, Object> image = new LinkedHashMap<>();
            image.put("image", "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(jpeg));
            content.add(image);
        }
        Map<String, Object> textNode = new LinkedHashMap<>();
        textNode.put("text", text);
        content.add(textNode);

        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", content);

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("messages", Collections.singletonList(message));

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("size", "2K");
        parameters.put("n", 1);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getImage().getModel());
        body.put("input", input);
        body.put("parameters", parameters);

        JsonNode root = post(multimodalEndpoint(), body, properties.getImage().getReadTimeoutMs());
        String url = null;
        JsonNode choices = root.path("output").path("choices");
        if (choices.isArray()) {
            for (JsonNode choice : choices) {
                JsonNode parts = choice.path("message").path("content");
                if (!parts.isArray()) {
                    continue;
                }
                for (JsonNode part : parts) {
                    if (part.has("image") && !part.get("image").asText().isEmpty()) {
                        url = part.get("image").asText();
                        break;
                    }
                }
                if (url != null) {
                    break;
                }
            }
        }
        return OssUrlValidator.toSecureUrl(url);
    }

    public byte[] downloadOss(String secureUrl, int maxBytes, int timeoutMs) {
        HttpJsonClient.DownloadResult result = http.download(
                secureUrl,
                maxBytes,
                properties.getBailian().getConnectTimeoutMs(),
                timeoutMs);
        if (result.getStatus() != 200 || result.getBody() == null) {
            throw upstream("模型结果下载失败");
        }
        String contentType = result.getContentType();
        if (contentType != null && !contentType.toLowerCase().startsWith("image/png")) {
            throw upstream("供应商图片类型不受支持");
        }
        return result.getBody();
    }

    public EmbeddingResult embeddings(List<String> texts, String textType) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("texts", texts);

        Map<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("dimension", properties.getEmbedding().getDimensions());
        parameters.put("text_type", textType);
        parameters.put("output_type", "dense");

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", properties.getEmbedding().getModel());
        body.put("input", input);
        body.put("parameters", parameters);

        JsonNode root = post(embeddingEndpoint(), body, properties.getEmbedding().getReadTimeoutMs());
        JsonNode output = root.get("output");
        if (output == null || !output.has("embeddings") || !output.get("embeddings").isArray()) {
            throw upstream("向量服务返回格式异常");
        }
        int size = texts.size();
        List<List<Double>> vectors = new ArrayList<>(Collections.nCopies(size, (List<Double>) null));
        boolean[] seen = new boolean[size];
        for (JsonNode item : output.get("embeddings")) {
            if (!item.has("text_index") || !item.has("embedding")) {
                throw upstream("向量服务返回缺少索引或向量");
            }
            int index = item.get("text_index").asInt(-1);
            if (index < 0 || index >= size || seen[index]) {
                throw upstream("向量服务返回索引重复或越界");
            }
            seen[index] = true;
            JsonNode embedding = item.get("embedding");
            if (!embedding.isArray() || embedding.size() != properties.getEmbedding().getDimensions()) {
                throw upstream("向量维度不符合约定");
            }
            List<Double> vector = new ArrayList<>(embedding.size());
            double norm = 0d;
            for (JsonNode value : embedding) {
                double number = value.asDouble();
                if (Double.isNaN(number) || Double.isInfinite(number)) {
                    throw upstream("向量包含非法数值");
                }
                norm += number * number;
                vector.add(number);
            }
            if (norm <= 0d) {
                throw upstream("向量范数为零");
            }
            vectors.set(index, vector);
        }
        for (boolean value : seen) {
            if (!value) {
                throw upstream("向量服务返回数量与输入不一致");
            }
        }
        Object usage = root.has("usage")
                ? objectMapper.convertValue(root.get("usage"), Object.class)
                : null;
        return new EmbeddingResult(vectors, usage);
    }

    private JsonNode post(String url, Map<String, Object> body, int readTimeoutMs) {
        HttpJsonClient.JsonResult result = http.postJson(
                url, headers(), body,
                properties.getBailian().getConnectTimeoutMs(),
                readTimeoutMs);
        if (result.getStatus() >= 400) {
            throw mapError(result.getStatus(), result.getBody());
        }
        try {
            JsonNode root = objectMapper.readTree(result.getBody());
            if (root.has("code") && !root.get("code").isNull()
                    && !isSuccessCode(root.get("code").asText())) {
                throw mapError(result.getStatus(), result.getBody());
            }
            return root;
        } catch (ClientApiException e) {
            throw e;
        } catch (Exception e) {
            throw upstream("模型服务返回异常");
        }
    }

    private Map<String, String> headers() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + properties.getBailian().getApiKey());
        return headers;
    }

    private String origin() {
        String origin = properties.getBailian().getOrigin();
        while (origin.endsWith("/")) {
            origin = origin.substring(0, origin.length() - 1);
        }
        return origin;
    }

    private String asrEndpoint() {
        return origin() + "/compatible-mode/v1/chat/completions";
    }

    private String multimodalEndpoint() {
        return origin() + "/api/v1/services/aigc/multimodal-generation/generation";
    }

    private String embeddingEndpoint() {
        return origin() + "/api/v1/services/embeddings/text-embedding/text-embedding";
    }

    private ClientApiException mapError(int status, String body) {
        String message = providerMessage(body);
        String lower = message == null ? "" : message.toLowerCase();
        if (status == 429) {
            return new ClientApiException(429, ClientCodes.RATE_LIMITED, "请求过于频繁，请稍后重试。");
        }
        if (status == 403 && (lower.contains("quota") || lower.contains("balance")
                || lower.contains("insufficient") || lower.contains("arrearage")
                || lower.contains("overdue") || lower.contains("good standing"))) {
            return new ClientApiException(403, ClientCodes.QUOTA_EXCEEDED, "模型服务额度不足");
        }
        if (status == 401 || status == 403) {
            return new ClientApiException(503, ClientCodes.SERVICE_UNAVAILABLE, "模型服务暂不可用");
        }
        if (lower.contains("content") || lower.contains("safety") || lower.contains("moderation")
                || lower.contains("inspection") || lower.contains("risk")
                || lower.contains("敏感")) {
            return new ClientApiException(422, ClientCodes.CONTENT_REJECTED, "内容未通过审核。");
        }
        return new ClientApiException(503, ClientCodes.SERVICE_UNAVAILABLE, "模型服务暂不可用");
    }

    private boolean isSuccessCode(String code) {
        if (code == null || code.isEmpty()) {
            return true;
        }
        return "Success".equalsIgnoreCase(code) || "0".equals(code) || "200".equals(code);
    }

    private String providerMessage(String body) {
        if (body == null || body.isEmpty()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root.has("message")) {
                return root.get("message").asText();
            }
            if (root.has("error")) {
                JsonNode error = root.get("error");
                return error.isTextual() ? error.asText() : error.path("message").asText(null);
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private int remainingMs(long deadline) {
        long remaining = deadline - System.currentTimeMillis();
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, remaining));
    }

    private ClientApiException upstream(String message) {
        return new ClientApiException(503, ClientCodes.SERVICE_UNAVAILABLE, message);
    }

    public static class EmbeddingResult {
        private final List<List<Double>> vectors;
        private final Object usage;

        public EmbeddingResult(List<List<Double>> vectors, Object usage) {
            this.vectors = vectors;
            this.usage = usage;
        }

        public List<List<Double>> getVectors() {
            return vectors;
        }

        public Object getUsage() {
            return usage;
        }
    }
}
