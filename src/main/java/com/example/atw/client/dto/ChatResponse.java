package com.example.atw.client.dto;

import lombok.Data;

import java.util.List;

@Data
public class ChatResponse {
    private String requestId;
    private List<ChatChoice> choices;
    private ChatUsage usage;
}
