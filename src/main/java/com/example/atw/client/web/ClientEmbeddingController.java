package com.example.atw.client.web;

import com.example.atw.client.dto.EmbeddingRequest;
import com.example.atw.client.error.ClientApiException;
import com.example.atw.client.service.ClientIdentityService;
import com.example.atw.client.service.EmbeddingService;
import com.example.atw.client.support.ClientJson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/api/v1/client")
public class ClientEmbeddingController {

    private static final int EMBEDDING_MAX_BYTES = 1024 * 1024;

    @Autowired
    private ClientJson clientJson;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private ClientIdentityService identityService;

    @PostMapping(value = "/embeddings", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Object> embeddings(@RequestBody(required = false) String raw,
                                             @RequestHeader(value = "X-Request-ID", required = false) String headerId,
                                             HttpServletRequest httpRequest) {
        EmbeddingRequest request = clientJson.parse(raw, EMBEDDING_MAX_BYTES, EmbeddingRequest.class);
        String textType = request.getTextType();
        if (!"document".equals(textType) && !"query".equals(textType)) {
            throw ClientApiException.invalid("textType 必须为 document 或 query");
        }
        Integer userId = "query".equals(textType)
                ? identityService.requireOwnerId(httpRequest)
                : identityService.requireAuthenticatedUserId(httpRequest);
        String requestId = clientJson.requireRequestId(headerId, request.getRequestId());
        request.setRequestId(requestId);
        Object result = embeddingService.embed(userId, request, requestId);
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(result);
    }
}
