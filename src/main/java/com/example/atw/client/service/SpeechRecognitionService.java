package com.example.atw.client.service;

import com.example.atw.client.config.ClientModelProperties;
import com.example.atw.client.dto.SpeechRecognitionResponse;
import com.example.atw.client.entity.ClientRequestRecord;
import com.example.atw.client.error.ClientApiException;
import com.example.atw.client.error.ClientCodes;
import com.example.atw.client.provider.BailianClient;
import com.example.atw.client.support.ClientHashes;
import com.example.atw.client.support.WavUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SpeechRecognitionService {

    @Autowired
    private ClientModelProperties properties;

    @Autowired
    private ClientRequestService requestService;

    @Autowired
    private BailianClient bailianClient;

    @Autowired
    private ObjectMapper objectMapper;

    public Object recognize(Integer userId, byte[] audio, String headerRequestId) {
        if (headerRequestId == null || headerRequestId.trim().isEmpty()) {
            throw ClientApiException.invalid("X-Request-ID 不能为空");
        }
        String requestId = headerRequestId.trim();
        if (requestId.length() > 100) {
            throw ClientApiException.invalid("X-Request-ID 最多 100 字符");
        }
        WavUtil.validateRecording(audio, properties.getAsr().getMaxBytes());

        String hash = ClientHashes.sha256(audio);
        ClientRequestService.Begin begin = requestService.begin(
                userId, requestId, ClientRequestService.OP_VOICE_RECOGNIZE, hash, null, null);
        if (begin.isReplay()) {
            return replay(begin.getRecord());
        }
        ClientRequestRecord record = begin.getRecord();
        try {
            String text = bailianClient.recognize(audio);
            SpeechRecognitionResponse response = new SpeechRecognitionResponse(text, requestId);
            requestService.complete(record, 200, response);
            return response;
        } catch (ClientApiException e) {
            requestService.fail(record, e.getStatus(), e.getCode(), e.getMessage());
            throw e.withRequestId(requestId);
        } catch (RuntimeException e) {
            requestService.fail(record, 500, ClientCodes.INTERNAL_ERROR, "语音识别失败");
            throw new ClientApiException(500, ClientCodes.INTERNAL_ERROR, "语音识别失败", requestId);
        }
    }

    private Object replay(ClientRequestRecord record) {
        if (ClientRequestRecord.STATE_COMPLETED.equals(record.getState())) {
            if (record.getResultJson() == null) {
                throw new ClientApiException(500, ClientCodes.INTERNAL_ERROR, "恢复结果为空");
            }
            try {
                JsonNode node = objectMapper.readTree(record.getResultJson());
                return node;
            } catch (JsonProcessingException e) {
                throw new ClientApiException(500, ClientCodes.INTERNAL_ERROR, "恢复结果解析失败");
            }
        }
        if (ClientRequestRecord.STATE_FAILED.equals(record.getState())) {
            throw new ClientApiException(record.getHttpStatus() == null ? 503 : record.getHttpStatus(),
                    record.getCode() == null ? ClientCodes.UPSTREAM_ERROR : record.getCode(),
                    record.getError() == null ? "语音识别失败" : record.getError(),
                    record.getRequestId());
        }
        throw new ClientApiException(409, ClientCodes.REQUEST_CANCELLED, "该请求已停止", record.getRequestId());
    }
}
