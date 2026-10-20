package com.example.atw.client.web;

import com.example.atw.client.config.ClientModelProperties;
import com.example.atw.client.dto.SpeechSynthesizeRequest;
import com.example.atw.client.error.ClientApiException;
import com.example.atw.client.error.ClientCodes;
import com.example.atw.client.service.ClientIdentityService;
import com.example.atw.client.service.SpeechRecognitionService;
import com.example.atw.client.service.SpeechSynthesisService;
import com.example.atw.client.support.ClientJson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/client")
public class ClientVoiceController {

    private static final int SYNTHESIZE_MAX_BYTES = 2 * 1024 * 1024;

    @Autowired
    private ClientJson clientJson;

    @Autowired
    private ClientModelProperties properties;

    @Autowired
    private SpeechRecognitionService recognitionService;

    @Autowired
    private SpeechSynthesisService synthesisService;

    @Autowired
    private ClientIdentityService identityService;

    @PostMapping(value = "/voice/recognize",
            consumes = {"audio/wav", "audio/x-wav", "application/octet-stream"})
    public ResponseEntity<Object> recognize(@RequestBody(required = false) byte[] audio,
                                            @RequestHeader(value = "X-Request-ID", required = false) String headerId,
                                            @RequestHeader(value = "Content-Length", required = false) Long contentLength,
                                            HttpServletRequest httpRequest) {
        if (contentLength != null && contentLength > properties.getAsr().getMaxBytes()) {
            throw new ClientApiException(413, ClientCodes.PAYLOAD_TOO_LARGE, "录音文件过大");
        }
        if (audio == null || audio.length == 0) {
            throw ClientApiException.invalid("录音内容不能为空");
        }
        Integer userId = identityService.requireOwnerId(httpRequest);
        Object result = recognitionService.recognize(userId, audio, headerId);
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(result);
    }

    @PostMapping(value = "/voice/synthesize", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> synthesize(@RequestBody(required = false) String raw,
                                             @RequestHeader(value = "X-Request-ID", required = false) String headerId,
                                             HttpServletRequest httpRequest) {
        Integer userId = identityService.requireOwnerId(httpRequest);
        SpeechSynthesizeRequest request = clientJson.parse(raw, SYNTHESIZE_MAX_BYTES, SpeechSynthesizeRequest.class);
        String requestId = clientJson.requireRequestId(headerId, request.getRequestId());
        request.setRequestId(requestId);
        Object result = synthesisService.synthesize(userId, request, requestId);
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(result);
    }
}
