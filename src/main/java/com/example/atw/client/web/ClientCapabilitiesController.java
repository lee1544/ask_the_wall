package com.example.atw.client.web;

import com.example.atw.client.service.ClientCapabilitiesService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/client")
public class ClientCapabilitiesController {

    @Autowired
    private ClientCapabilitiesService capabilitiesService;

    @GetMapping("/capabilities")
    public ResponseEntity<Map<String, Object>> capabilities(
            @RequestHeader(value = "X-License", required = false) String licenseHeader) {
        return ResponseEntity.ok()
                .header("Cache-Control", "no-store")
                .body(capabilitiesService.capabilities(licenseHeader));
    }
}
