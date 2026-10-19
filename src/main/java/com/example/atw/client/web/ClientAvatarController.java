package com.example.atw.client.web;

import com.example.atw.client.config.ClientModelProperties;
import com.example.atw.client.dto.AvatarJobDto;
import com.example.atw.client.dto.AvatarRequest;
import com.example.atw.client.service.AvatarService;
import com.example.atw.client.support.ClientJson;
import com.example.atw.utils.UserContextUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/client")
public class ClientAvatarController {

    @Autowired
    private ClientModelProperties properties;

    @Autowired
    private ClientJson clientJson;

    @Autowired
    private AvatarService avatarService;

    @PostMapping(value = "/avatar/generate", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> generate(@RequestBody(required = false) String raw,
                                           @RequestHeader(value = "X-Request-ID", required = false) String headerId) {
        Integer userId = UserContextUtil.getCurrentUserId();
        AvatarRequest request = clientJson.parse(raw, properties.getImage().getMaxRequestBytes(), AvatarRequest.class);
        String requestId = clientJson.requireRequestId(headerId, request.getRequestId());
        request.setRequestId(requestId);

        AvatarService.AvatarOutcome outcome = avatarService.create(userId, request, requestId);
        if (outcome.isNewJob()) {
            avatarService.submit(outcome.getJobId());
        }
        return ResponseEntity.status(outcome.getStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .header("Cache-Control", "no-store")
                .body(outcome.getBody());
    }

    @GetMapping("/image-jobs/{id}")
    public ResponseEntity<AvatarJobDto> get(@PathVariable String id) {
        Integer userId = UserContextUtil.getCurrentUserId();
        AvatarJobDto job = avatarService.get(userId, id);
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(job);
    }

    @DeleteMapping("/image-jobs/{id}")
    public ResponseEntity<AvatarJobDto> cancel(@PathVariable String id) {
        Integer userId = UserContextUtil.getCurrentUserId();
        AvatarJobDto job = avatarService.cancel(userId, id);
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(job);
    }
}
