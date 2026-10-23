package com.example.atw.web;

import com.example.atw.annotation.RequirePermission;
import com.example.atw.client.dto.ChatModelConfigRequest;
import com.example.atw.client.entity.ChatModelConfig;
import com.example.atw.client.service.ChatModelConfigService;
import com.example.atw.exception.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/model-configs")
public class ChatModelConfigController {

    @Autowired
    private ChatModelConfigService modelConfigService;

    @RequirePermission("model:view")
    @GetMapping
    public List<ChatModelConfig> list() {
        return modelConfigService.list();
    }

    @RequirePermission("model:view")
    @GetMapping("/{id}")
    public ChatModelConfig get(@PathVariable Long id) {
        return modelConfigService.get(id);
    }

    @RequirePermission("model:update")
    @PostMapping
    public ChatModelConfig create(@Valid @RequestBody ChatModelConfigRequest request) {
        return modelConfigService.create(request);
    }

    @RequirePermission("model:update")
    @PutMapping("/{id}")
    public ChatModelConfig update(@PathVariable Long id,
                                  @Valid @RequestBody ChatModelConfigRequest request) {
        return modelConfigService.update(id, request);
    }

    @RequirePermission("model:update")
    @PostMapping("/{id}/activate")
    public ChatModelConfig activate(@PathVariable Long id) {
        return modelConfigService.activate(id);
    }

    @RequirePermission("model:update")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        modelConfigService.delete(id);
        return new ApiResponse<>(200, "模型配置已删除");
    }

    @RequirePermission("model:update")
    @PostMapping("/{id}/test")
    public Map<String, Object> test(@PathVariable Long id) {
        return modelConfigService.testConnection(id);
    }
}
