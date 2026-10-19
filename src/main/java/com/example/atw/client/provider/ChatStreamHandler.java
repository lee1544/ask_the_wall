package com.example.atw.client.provider;

import com.example.atw.client.dto.ChatUsage;

public interface ChatStreamHandler {

    void onDelta(String content);

    void onFinish(String finishReason, ChatUsage usage);

    void onError(String code, String message);
}
