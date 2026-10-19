package com.example.atw.client.service;

import com.example.atw.client.config.ClientModelProperties;
import com.example.atw.client.entity.ClientRequestRecord;
import com.example.atw.client.error.ClientApiException;
import com.example.atw.client.error.ClientCodes;
import com.example.atw.client.repository.ClientRequestRecordRepository;
import com.example.atw.client.support.ClientTime;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class ClientRequestService {

    public static final String OP_CHAT = "chat";
    public static final String OP_VOICE_RECOGNIZE = "voice-recognize";
    public static final String OP_VOICE_SYNTHESIZE = "voice-synthesize";
    public static final String OP_AVATAR_GENERATE = "avatar-generate";
    public static final String OP_EMBEDDINGS = "embeddings";

    @Autowired
    private ClientRequestRecordRepository repository;

    @Autowired
    private ClientModelProperties properties;

    @Autowired
    private ObjectMapper objectMapper;

    public Begin begin(Integer userId, String requestId, String operation, String requestHash,
                       String generationId, String purpose) {
        if (userId == null) {
            throw new ClientApiException(401, ClientCodes.AUTH_EXPIRED, "登录状态已失效");
        }
        validateRequestId(requestId);

        ClientRequestRecord existing = repository.findByUserIdAndRequestId(userId, requestId).orElse(null);
        if (existing != null) {
            return resolveExisting(existing, operation, requestHash);
        }

        ClientRequestRecord record = new ClientRequestRecord();
        record.setUserId(userId);
        record.setRequestId(requestId);
        record.setOperation(operation);
        record.setRequestHash(requestHash);
        record.setGenerationId(generationId);
        record.setPurpose(purpose);
        record.setState(ClientRequestRecord.STATE_RUNNING);
        record.setCreatedAt(ClientTime.now());
        record.setUpdatedAt(record.getCreatedAt());
        record.setExpiresAt(ClientTime.expireAfterHours(properties.getRequests().getRetentionHours()));

        try {
            repository.saveAndFlush(record);
            return new Begin(record, false);
        } catch (DataIntegrityViolationException e) {
            ClientRequestRecord raced = repository.findByUserIdAndRequestId(userId, requestId)
                    .orElseThrow(() -> new ClientApiException(409, ClientCodes.REQUEST_IN_PROGRESS,
                            "相同 requestId 的请求正在处理中"));
            return resolveExisting(raced, operation, requestHash);
        }
    }

    private Begin resolveExisting(ClientRequestRecord existing, String operation, String requestHash) {
        if (!operation.equals(existing.getOperation())
                || (requestHash != null && !requestHash.equals(existing.getRequestHash()))) {
            throw new ClientApiException(409, ClientCodes.REQUEST_CONFLICT,
                    "相同 requestId 已用于其他接口或不同参数");
        }
        if (!ClientRequestRecord.STATE_RUNNING.equals(existing.getState())
                && ClientTime.isExpired(existing.getExpiresAt())) {
            throw new ClientApiException(410, ClientCodes.REQUEST_EXPIRED,
                    "请求记录已过期", existing.getRequestId());
        }
        if (ClientRequestRecord.STATE_RUNNING.equals(existing.getState())) {
            throw new ClientApiException(409, ClientCodes.REQUEST_IN_PROGRESS,
                    "相同 requestId 的请求正在处理中", existing.getRequestId());
        }
        return new Begin(existing, true);
    }

    public void complete(ClientRequestRecord record, int httpStatus, Object result) {
        if (record == null) {
            return;
        }
        ClientRequestRecord current = reload(record);
        if (!ClientRequestRecord.STATE_RUNNING.equals(current.getState())) {
            return;
        }
        current.setState(ClientRequestRecord.STATE_COMPLETED);
        current.setHttpStatus(httpStatus);
        current.setResultJson(toJson(result));
        current.setError(null);
        current.setCode(null);
        touch(current);
        repository.save(current);
    }

    public void fail(ClientRequestRecord record, int httpStatus, String code, String error) {
        if (record == null) {
            return;
        }
        ClientRequestRecord current = reload(record);
        if (!ClientRequestRecord.STATE_RUNNING.equals(current.getState())) {
            return;
        }
        current.setState(ClientRequestRecord.STATE_FAILED);
        current.setHttpStatus(httpStatus);
        current.setError(error);
        current.setCode(code);
        touch(current);
        repository.save(current);
    }

    public void stop(ClientRequestRecord record, String partialContent) {
        if (record == null) {
            return;
        }
        ClientRequestRecord current = reload(record);
        if (ClientRequestRecord.STATE_COMPLETED.equals(current.getState())
                || ClientRequestRecord.STATE_FAILED.equals(current.getState())) {
            return;
        }
        if (ClientRequestRecord.STATE_STOPPED.equals(current.getState())) {
            if (partialContent != null && !partialContent.isEmpty()) {
                current.setPartialContent(partialContent);
                current.setUpdatedAt(ClientTime.now());
                repository.save(current);
            }
            return;
        }
        current.setState(ClientRequestRecord.STATE_STOPPED);
        current.setHttpStatus(499);
        current.setPartialContent(partialContent);
        touch(current);
        repository.save(current);
    }

    public ClientRequestRecord get(Integer userId, String requestId) {
        validateRequestId(requestId);
        ClientRequestRecord record = repository.findByUserIdAndRequestId(userId, requestId)
                .orElseThrow(() -> new ClientApiException(404, ClientCodes.REQUEST_NOT_FOUND,
                        "请求记录不存在", requestId));
        if (!ClientRequestRecord.STATE_RUNNING.equals(record.getState())
                && ClientTime.isExpired(record.getExpiresAt())) {
            throw new ClientApiException(410, ClientCodes.REQUEST_EXPIRED, "请求记录已过期", requestId);
        }
        return record;
    }

    public java.util.List<ClientRequestRecord> findByGeneration(Integer userId, String generationId) {
        return repository.findByUserIdAndGenerationId(userId, generationId);
    }

    @Transactional
    public void purgeExpired() {
        repository.deleteExpired(ClientTime.now());
    }

    private ClientRequestRecord reload(ClientRequestRecord record) {
        if (record.getId() == null) {
            return record;
        }
        return repository.findById(record.getId()).orElse(record);
    }

    private void touch(ClientRequestRecord record) {
        record.setUpdatedAt(ClientTime.now());
        record.setExpiresAt(ClientTime.expireAfterHours(properties.getRequests().getRetentionHours()));
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new ClientApiException(500, ClientCodes.INTERNAL_ERROR, "结果序列化失败");
        }
    }

    private void validateRequestId(String requestId) {
        if (requestId == null || requestId.trim().isEmpty()) {
            throw ClientApiException.invalid("requestId 不能为空");
        }
        if (requestId.length() > 100) {
            throw ClientApiException.invalid("requestId 最多 100 字符");
        }
    }

    public static class Begin {
        private final ClientRequestRecord record;
        private final boolean replay;

        public Begin(ClientRequestRecord record, boolean replay) {
            this.record = record;
            this.replay = replay;
        }

        public ClientRequestRecord getRecord() {
            return record;
        }

        public boolean isReplay() {
            return replay;
        }
    }
}
