package com.example.atw.client.support;

import com.example.atw.client.error.ClientApiException;
import com.example.atw.client.error.ClientCodes;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class ClientJson {

    @Autowired
    private ObjectMapper objectMapper;

    public <T> T parse(String raw, int maxBytes, Class<T> type) {
        if (raw == null || raw.trim().isEmpty()) {
            throw ClientApiException.invalid("请求体不能为空");
        }
        if (raw.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new ClientApiException(413, ClientCodes.PAYLOAD_TOO_LARGE, "请求体过大");
        }
        try {
            return objectMapper.readerFor(type)
                    .with(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(raw);
        } catch (JsonProcessingException e) {
            throw ClientApiException.invalid("请求体不是合法 JSON 或包含不支持的字段");
        }
    }

    public String requireRequestId(String header, String bodyValue) {
        String headerValue = header == null ? null : header.trim();
        String body = bodyValue == null ? null : bodyValue.trim();
        if (headerValue != null && !headerValue.isEmpty() && body != null && !body.isEmpty()
                && !headerValue.equals(body)) {
            throw ClientApiException.invalid("X-Request-ID 与请求体 requestId 不一致");
        }
        String requestId = (headerValue != null && !headerValue.isEmpty()) ? headerValue : body;
        if (requestId == null || requestId.isEmpty()) {
            throw ClientApiException.invalid("requestId 不能为空");
        }
        if (requestId.length() > 100) {
            throw ClientApiException.invalid("requestId 最多 100 字符");
        }
        return requestId;
    }

    public void requireSameRequestId(String header, String bodyValue) {
        requireRequestId(header, bodyValue);
    }
}
