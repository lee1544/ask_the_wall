package com.example.atw.client.web;

import com.example.atw.client.config.ClientModelProperties;
import com.example.atw.client.dto.ChatRequest;
import com.example.atw.client.dto.RequestStatusResponse;
import com.example.atw.client.dto.StopResponse;
import com.example.atw.client.service.ChatService;
import com.example.atw.client.support.ClientJson;
import com.example.atw.utils.UserContextUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/client")
public class ClientChatController {

    @Autowired
    private ClientModelProperties properties;

    @Autowired
    private ClientJson clientJson;

    @Autowired
    private ChatService chatService;

    @PostMapping(value = "/chat/completions", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> completions(@RequestBody(required = false) String raw,
                                         @RequestHeader(value = "X-Request-ID", required = false) String headerId) {
        Integer userId = UserContextUtil.getCurrentUserId();
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
                    .body(outcome.getReplay());
        }
        return ResponseEntity.ok()
                .header("X-Config-Version", outcome.getConfigVersion())
                .header("Cache-Control", "no-store")
                .body(outcome.getResponse());
    }

    @PostMapping("/chat/{generationId}/stop")
    public ResponseEntity<StopResponse> stop(@PathVariable String generationId,
                                             @RequestBody(required = false) String raw) {
        Integer userId = UserContextUtil.getCurrentUserId();
        StopResponse response = chatService.stop(userId, generationId);
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(response);
    }

    @GetMapping("/requests/{requestId}")
    public ResponseEntity<RequestStatusResponse> status(@PathVariable String requestId) {
        Integer userId = UserContextUtil.getCurrentUserId();
        RequestStatusResponse response = chatService.status(userId, requestId);
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(response);
    }
}
