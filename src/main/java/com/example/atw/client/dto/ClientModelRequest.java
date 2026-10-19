package com.example.atw.client.dto;

import com.example.atw.client.error.ClientApiException;
import com.example.atw.client.error.ClientCodes;
import lombok.Data;

/**
 * 普通请求不得覆盖模型/供应商配置。
 */
@Data
public class ClientModelRequest {
    private String model;
    private String baseUrl;
    private String apiKey;
    private String region;
    private String workspaceId;

    public void rejectOverrides(String requestId) {
        if (model != null || baseUrl != null || apiKey != null || region != null || workspaceId != null) {
            throw new ClientApiException(400, ClientCodes.INVALID_ARGUMENT,
                    "请求不能覆盖模型或供应商配置", requestId);
        }
    }
}
