package com.example.atw.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class SpeechRecognitionResponse {
    private String text;
    private String requestId;
}
