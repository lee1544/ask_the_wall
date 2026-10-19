package com.example.atw.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = false)
public class ChatRequest extends ClientModelRequest {

    private String requestId;
    private String generationId;
    private String purpose;
    private List<ChatMessage> messages;
    private Boolean stream;
    private String expectedPolicyVersion;

    @JsonProperty("max_tokens")
    private Integer maxTokens;
}
