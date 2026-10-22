package com.example.atw.client.provider;

import com.example.atw.client.dto.ChatToolCall;
import com.example.atw.client.dto.ChatUsage;

import java.util.List;

public interface ChatStreamHandler {

    void onDelta(String content);

    void onFinish(String finishReason, ChatUsage usage, List<ChatToolCall> toolCalls);

    void onError(String code, String message);
}
