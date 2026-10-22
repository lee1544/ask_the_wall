package com.example.atw.client.service;

import com.example.atw.client.config.ClientModelProperties;
import com.example.atw.client.dto.SpeechSynthesizeRequest;
import com.example.atw.client.dto.SpeechSynthesizeResponse;
import com.example.atw.client.entity.ClientMedia;
import com.example.atw.client.entity.ClientRequestRecord;
import com.example.atw.client.error.ClientApiException;
import com.example.atw.client.error.ClientCodes;
import com.example.atw.client.provider.BailianClient;
import com.example.atw.client.support.ClientHashes;
import com.example.atw.client.support.ClientTime;
import com.example.atw.client.support.SpeechTextChunker;
import com.example.atw.client.support.WavUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class SpeechSynthesisService {

    @Autowired
    private ClientModelProperties properties;

    @Autowired
    private ClientRequestService requestService;

    @Autowired
    private BailianClient bailianClient;

    @Autowired
    private SpeechSegmenter segmenter;

    @Autowired
    private ClientMediaService mediaService;

    @Autowired
    private ObjectMapper objectMapper;

    public Object synthesize(Integer userId, SpeechSynthesizeRequest request, String requestId) {
        if (request == null) {
            throw ClientApiException.invalid("请求体不能为空");
        }
        request.rejectOverrides(requestId);
        if (request.getText() == null || request.getText().isEmpty()) {
            throw ClientApiException.invalid("text 不能为空");
        }
        if (request.getText().length() > properties.getTts().getMaxTextChars()) {
            throw ClientApiException.invalid("text 最多 " + properties.getTts().getMaxTextChars() + " 字符");
        }
        if (request.getExpectedConfigVersion() == null || request.getExpectedConfigVersion().trim().isEmpty()) {
            throw ClientApiException.invalid("expectedConfigVersion 不能为空");
        }
        int segmentIndex = request.getSegmentIndex() == null ? 0 : request.getSegmentIndex();
        String voice = resolveVoice(request.getVoice(), requestId);

        String hash = ClientHashes.sha256(request.getText() + "|" + segmentIndex + "|" + voice + "|"
                + request.getExpectedConfigVersion());
        ClientRequestService.Begin begin = requestService.begin(
                userId, requestId, ClientRequestService.OP_VOICE_SYNTHESIZE, hash, null, null);
        if (begin.isReplay()) {
            return replay(begin.getRecord());
        }
        ClientRequestRecord record = begin.getRecord();
        try {
            if (!properties.getTts().getConfigVersion().equals(request.getExpectedConfigVersion())) {
                throw new ClientApiException(409, ClientCodes.CONFIG_CHANGED, "语音配置已更新，请重新发起。", requestId);
            }
            List<String> segments = segmenter.segment(request.getText());
            if (segmentIndex < 0 || segmentIndex >= segments.size()) {
                throw ClientApiException.invalid("segmentIndex 越界");
            }
            String segmentText = segments.get(segmentIndex);
            String cacheKey = ClientHashes.sha256(properties.getTts().getConfigVersion() + "|"
                    + properties.getTts().getSegmentationVersion() + "|" + voice + "|" + segmentText);

            Optional<ClientMedia> cached = mediaService.findCached(userId, cacheKey);
            if (cached.isPresent()) {
                ClientMedia media = cached.get();
                SpeechSynthesizeResponse response = buildResponse(media, segmentIndex, segments.size(), voice, true);
                requestService.complete(record, 200, response);
                return response;
            }

            List<String> providerChunks = SpeechTextChunker.chunk(
                    segmentText, properties.getTts().getProviderChunkChars());
            List<byte[]> rawParts = new ArrayList<>(providerChunks.size());
            for (String providerChunk : providerChunks) {
                rawParts.add(bailianClient.synthesize(providerChunk, voice));
            }
            byte[] raw = rawParts.size() == 1
                    ? rawParts.get(0)
                    : WavUtil.concatSynthesized(rawParts, properties.getTts().getMaxAudioBytes());
            byte[] wav = WavUtil.normalizeSynthesized(raw, properties.getTts().getMaxAudioBytes());
            ClientMedia media = mediaService.save(userId, ClientMediaService.TYPE_AUDIO, "audio/wav", wav, cacheKey);
            SpeechSynthesizeResponse response = buildResponse(media, segmentIndex, segments.size(), voice, false);
            requestService.complete(record, 200, response);
            return response;
        } catch (ClientApiException e) {
            requestService.fail(record, e.getStatus(), e.getCode(), e.getMessage());
            throw e.withRequestId(requestId);
        } catch (RuntimeException e) {
            requestService.fail(record, 500, ClientCodes.INTERNAL_ERROR, "语音合成失败");
            throw new ClientApiException(500, ClientCodes.INTERNAL_ERROR, "语音合成失败", requestId);
        }
    }

    private SpeechSynthesizeResponse buildResponse(ClientMedia media, int segmentIndex,
                                                   int segmentCount, String voice, boolean cached) {
        SpeechSynthesizeResponse response = new SpeechSynthesizeResponse();
        response.setUrl(mediaService.url(media.getId()));
        response.setSegmentIndex(segmentIndex);
        response.setSegmentCount(segmentCount);
        response.setVoice(voice);
        response.setCached(cached);
        response.setConfigVersion(properties.getTts().getConfigVersion());
        response.setExpiresAt(ClientTime.iso(media.getExpiresAt()));
        return response;
    }

    private String resolveVoice(String requested, String requestId) {
        String defaultVoice = properties.getTts().getVoice();
        if (requested == null || requested.trim().isEmpty()) {
            return defaultVoice;
        }
        String value = requested.trim();
        List<String> voices = properties.getTts().getVoices();
        if (voices != null) {
            for (String voice : voices) {
                if (voice != null && voice.equalsIgnoreCase(value)) {
                    return voice;
                }
            }
        }
        if (defaultVoice != null && defaultVoice.equalsIgnoreCase(value)) {
            return defaultVoice;
        }
        String allowed = voices == null || voices.isEmpty() ? defaultVoice : String.join("/", voices);
        throw new ClientApiException(400, ClientCodes.INVALID_ARGUMENT,
                "voice 不受支持，可选：" + allowed, requestId);
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
                    record.getError() == null ? "语音合成失败" : record.getError(),
                    record.getRequestId());
        }
        throw new ClientApiException(409, ClientCodes.REQUEST_CANCELLED, "该请求已停止", record.getRequestId());
    }
}
