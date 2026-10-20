package com.example.atw.client.web;

import com.example.atw.client.config.ClientModelProperties;
import com.example.atw.client.dto.ChatRequest;
import com.example.atw.client.dto.RequestStatusResponse;
import com.example.atw.client.dto.StopResponse;
import com.example.atw.client.service.ChatService;
import com.example.atw.client.service.ClientIdentityService;
import com.example.atw.client.support.ClientJson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

@RestController
@RequestMapping("/api/v1/client")
public class ClientChatController {

    @Autowired
    private ClientModelProperties properties;

    @Autowired
    private ClientJson clientJson;

    @Autowired
    private ChatService chatService;

    @Autowired
    private ClientIdentityService identityService;

    @PostMapping(value = "/chat/completions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseBodyEmitter> completions(@RequestBody(required = false) String raw,
                                                           @RequestHeader(value = "X-Request-ID", required = false) String headerId,
                                                           HttpServletRequest httpRequest) {
        Integer userId = identityService.requireOwnerId(httpRequest);
        ChatRequest request = clientJson.parse(raw, properties.getChat().getMaxRequestBytes(), ChatRequest.class);
        String requestId = clientJson.requireRequestId(headerId, request.getRequestId());
        request.setRequestId(requestId);

        ChatService.ChatOutcome outcome = chatService.handle(userId, request, requestId);
        if (outcome.getEmitter() != null) {
            return ResponseEntity.ok()
                    .contentType(MediaType.TEXT_EVENT_STREAM)
                    .header("X-Config-Version", outcome.getConfigVersion())
                    .header("Cache-Control", "no-store")
                    .body(outcome.getEmitter());
        }
        if (outcome.getReplay() != null) {
            return ResponseEntity.status(outcome.getStatus())
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Config-Version", outcome.getConfigVersion())
                    .header("Cache-Control", "no-store")
                    .body(jsonEmitter(outcome.getReplay()));
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Config-Version", outcome.getConfigVersion())
                .header("Cache-Control", "no-store")
                .body(jsonEmitter(outcome.getResponse()));
    }

    private ResponseBodyEmitter jsonEmitter(Object payload) {
        ResponseBodyEmitter emitter = new ResponseBodyEmitter();
        try {
            emitter.send(payload, MediaType.APPLICATION_JSON);
            emitter.complete();
        } catch (IOException e) {
            throw new IllegalStateException("响应写入失败", e);
        }
        return emitter;
    }

    @PostMapping("/chat/{generationId}/stop")
    public ResponseEntity<StopResponse> stop(@PathVariable String generationId,
                                             @RequestBody(required = false) String raw,
                                             HttpServletRequest httpRequest) {
        Integer userId = identityService.requireOwnerId(httpRequest);
        StopResponse response = chatService.stop(userId, generationId);
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(response);
    }

    @GetMapping("/requests/{requestId}")
    public ResponseEntity<RequestStatusResponse> status(@PathVariable String requestId,
                                                        HttpServletRequest httpRequest) {
        Integer userId = identityService.requireOwnerId(httpRequest);
        RequestStatusResponse response = chatService.status(userId, requestId);
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(response);
    }
}
