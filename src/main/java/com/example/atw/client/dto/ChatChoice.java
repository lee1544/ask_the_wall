package com.example.atw.client.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class ChatChoice {
    private int index;
    private ChatMessage message;

    @JsonProperty("finish_reason")
    private String finishReason;
}
