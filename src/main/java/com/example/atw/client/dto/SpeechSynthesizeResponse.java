package com.example.atw.client.dto;

import lombok.Data;

@Data
public class SpeechSynthesizeResponse {
    private String url;
    private int segmentIndex;
    private int segmentCount;
    private boolean cached;
    private String configVersion;
    private String expiresAt;
}
