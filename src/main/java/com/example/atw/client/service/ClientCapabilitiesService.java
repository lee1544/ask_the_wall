package com.example.atw.client.service;

import com.example.atw.client.config.ChatModelRuntime;
import com.example.atw.client.config.ClientModelProperties;
import com.example.atw.dto.LicensePayload;
import com.example.atw.service.ActivationCodeService;
import com.example.atw.service.LicenseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class ClientCapabilitiesService {

    @Autowired
    private ClientModelProperties properties;

    @Autowired
    private LicenseService licenseService;

    @Autowired
    private ActivationCodeService activationCodeService;

    @Autowired
    private WebSearchService webSearchService;

    @Autowired
    private ChatModelConfigService modelConfigService;

    public Map<String, Object> capabilities(String licenseHeader) {
        boolean licensed = isLicensed(licenseHeader);
        ChatModelRuntime runtime = modelConfigService.runtime();
        boolean chatKey = runtime.hasApiKey();
        boolean bailianKey = hasText(properties.getBailian().getApiKey());

        Map<String, Object> chat = new LinkedHashMap<>();
        chat.put("available", licensed && chatKey);
        chat.put("contextBudget", runtime.getContextBudget());
        chat.put("reservedOutputTokens", runtime.getMaxOutputTokens());
        chat.put("maxOutputTokens", runtime.getMaxOutputTokens());
        chat.put("spaceId", runtime.getPolicyVersion());
        chat.put("maxRequestBytes", properties.getChat().getMaxRequestBytes());
        chat.put("maxMessages", properties.getChat().getMaxMessages());
        chat.put("maxResponseChars", properties.getChat().getMaxResponseChars());
        chat.put("webSearchAvailable", licensed && webSearchService.isAvailable(runtime));
        chat.put("maxToolRounds", properties.getChat().getMaxToolRounds());

        Map<String, Object> image = new LinkedHashMap<>();
        image.put("available", licensed && bailianKey);
        image.put("maxPromptChars", properties.getImage().getMaxPromptChars());
        image.put("maxReferenceBytes", properties.getImage().getMaxReferenceBytes());
        image.put("maxRequestBytes", properties.getImage().getMaxRequestBytes());
        image.put("configVersion", properties.getImage().getConfigVersion());

        Map<String, Object> voice = new LinkedHashMap<>();
        voice.put("available", licensed && bailianKey);
        voice.put("recognitionAvailable", licensed && bailianKey);
        voice.put("synthesisAvailable", licensed && bailianKey);
        voice.put("maxRecordingSeconds", 60);
        voice.put("maxRecordingBytes", properties.getAsr().getMaxBytes());
        voice.put("maxTextChars", properties.getTts().getMaxTextChars());
        voice.put("defaultVoice", properties.getTts().getVoice());
        voice.put("voices", properties.getTts().getVoices());
        voice.put("configVersion", properties.getTts().getConfigVersion());
        voice.put("segmentationVersion", properties.getTts().getSegmentationVersion());

        Map<String, Object> embedding = new LinkedHashMap<>();
        embedding.put("available", licensed && bailianKey);
        embedding.put("spaceId", properties.getEmbedding().getSpaceId());
        embedding.put("dimensions", properties.getEmbedding().getDimensions());
        embedding.put("maxBatchSize", properties.getEmbedding().getMaxBatchSize());
        embedding.put("maxTextChars", properties.getEmbedding().getMaxTextChars());
        embedding.put("maxResponseBytes", properties.getEmbedding().getMaxResponseBytes());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("chat", chat);
        response.put("image", image);
        response.put("voice", voice);
        response.put("embedding", embedding);
        return response;
    }

    private boolean isLicensed(String licenseHeader) {
        if (!licenseService.isEnforceEnabled()) {
            return true;
        }
        if (!hasText(licenseHeader)) {
            return false;
        }
        try {
            LicensePayload payload = licenseService.verify(licenseHeader);
            return activationCodeService.isActiveLicense(payload.getLicenseId());
        } catch (Exception e) {
            return false;
        }
    }

    private boolean hasText(String value) {
        return StringUtils.hasText(value);
    }
}
