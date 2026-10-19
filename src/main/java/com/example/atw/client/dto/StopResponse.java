package com.example.atw.client.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class StopResponse {
    private boolean ok;
    private String generationId;
    private String state;
}
