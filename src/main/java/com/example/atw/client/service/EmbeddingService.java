package com.example.atw.client.service;

import com.example.atw.client.config.ClientModelProperties;
import com.example.atw.client.dto.EmbeddingRequest;
import com.example.atw.client.dto.EmbeddingResponse;
import com.example.atw.client.entity.ClientRequestRecord;
import com.example.atw.client.error.ClientApiException;
import com.example.atw.client.error.ClientCodes;
import com.example.atw.client.provider.BailianClient;
import com.example.atw.client.support.ClientHashes;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class EmbeddingService {

    @Autowired
    private ClientModelProperties properties;

    @Autowired
    private ClientRequestService requestService;

    @Autowired
    private BailianClient bailianClient;

    @Autowired
    private ObjectMapper objectMapper;

    public Object embed(Integer userId, EmbeddingRequest request, String requestId) {
        if (request == null) {
            throw ClientApiException.invalid("请求体不能为空");
        }
        request.rejectOverrides(requestId);
        List<String> texts = request.getTexts();
        if (texts == null || texts.isEmpty()) {
            throw ClientApiException.invalid("texts 至少 1 段");
        }
        if (texts.size() > properties.getEmbedding().getMaxBatchSize()) {
            throw ClientApiException.invalid("texts 最多 " + properties.getEmbedding().getMaxBatchSize() + " 段");
        }
        for (String text : texts) {
            if (text == null || text.isEmpty()) {
                throw ClientApiException.invalid("texts 不能包含空字符串");
            }
            if (text.length() > properties.getEmbedding().getMaxTextChars()) {
                throw ClientApiException.invalid("每段文本最多 " + properties.getEmbedding().getMaxTextChars() + " 字符");
            }
        }
        String textType = request.getTextType();
        if (!"document".equals(textType) && !"query".equals(textType)) {
            throw ClientApiException.invalid("textType 必须为 document 或 query");
        }
        if (request.getExpectedSpaceId() == null || request.getExpectedSpaceId().trim().isEmpty()) {
            throw ClientApiException.invalid("expectedSpaceId 不能为空");
        }

        StringBuilder hashSource = new StringBuilder();
        hashSource.append(textType).append('|').append(request.getExpectedSpaceId());
        for (String text : texts) {
            hashSource.append('|').append(text);
        }
        String hash = ClientHashes.sha256(hashSource.toString());

        ClientRequestService.Begin begin = requestService.begin(
                userId, requestId, ClientRequestService.OP_EMBEDDINGS, hash, null, null);
        if (begin.isReplay()) {
            return replay(begin.getRecord());
        }
        ClientRequestRecord record = begin.getRecord();
        try {
            if (!properties.getEmbedding().getSpaceId().equals(request.getExpectedSpaceId())) {
                throw new ClientApiException(409, ClientCodes.INDEX_SPACE_CHANGED,
                        "向量配置已变化，请刷新服务信息并重建索引。", requestId);
            }
            BailianClient.EmbeddingResult result = bailianClient.embeddings(texts, textType);
            EmbeddingResponse response = new EmbeddingResponse();
            response.setVectors(result.getVectors());
            response.setDimensions(properties.getEmbedding().getDimensions());
            response.setSpaceId(properties.getEmbedding().getSpaceId());
            response.setUsage(result.getUsage());
            response.setRequestId(requestId);
            requestService.complete(record, 200, response);
            return response;
        } catch (ClientApiException e) {
            requestService.fail(record, e.getStatus(), e.getCode(), e.getMessage());
            throw e.withRequestId(requestId);
        } catch (RuntimeException e) {
            requestService.fail(record, 500, ClientCodes.INTERNAL_ERROR, "向量生成失败");
            throw new ClientApiException(500, ClientCodes.INTERNAL_ERROR, "向量生成失败", requestId);
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
                    record.getError() == null ? "向量生成失败" : record.getError(),
                    record.getRequestId());
        }
        throw new ClientApiException(409, ClientCodes.REQUEST_CANCELLED, "该请求已停止", record.getRequestId());
    }
}
