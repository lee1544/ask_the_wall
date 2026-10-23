package com.example.atw.client.provider;

import com.example.atw.client.config.ChatModelRuntime;
import com.example.atw.client.config.WebSearchMode;
import com.example.atw.client.dto.ChatMessage;
import com.example.atw.client.service.WebSearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DeepSeekClientTest {

    @Test
    @SuppressWarnings("unchecked")
    void enablesQwenSearchWithoutExposingDeepSeekTools() {
        DeepSeekClient client = client();
        ChatModelRuntime runtime = runtime(WebSearchMode.QWEN_ENABLE_SEARCH);

        Map<String, Object> body = (Map<String, Object>) ReflectionTestUtils.invokeMethod(
                client, "buildBody", runtime, messages(), true, 512, true);

        assertEquals(Boolean.TRUE, body.get("enable_search"));
        assertFalse(body.containsKey("tools"));
        assertFalse(body.containsKey("tool_choice"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void exposesDeepSeekToolsInDeepSeekMode() {
        DeepSeekClient client = client();
        ChatModelRuntime runtime = runtime(WebSearchMode.DEEPSEEK_TOOLS);

        Map<String, Object> body = (Map<String, Object>) ReflectionTestUtils.invokeMethod(
                client, "buildBody", runtime, messages(), true, 512, true);

        assertFalse(body.containsKey("enable_search"));
        assertTrue(body.containsKey("tools"));
        assertEquals("auto", body.get("tool_choice"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void disablesSearchForSummaryRequests() {
        DeepSeekClient client = client();
        ChatModelRuntime runtime = runtime(WebSearchMode.QWEN_ENABLE_SEARCH);

        Map<String, Object> body = (Map<String, Object>) ReflectionTestUtils.invokeMethod(
                client, "buildBody", runtime, messages(), false, 512, false);

        assertFalse(body.containsKey("enable_search"));
        assertFalse(body.containsKey("tools"));
    }

    private DeepSeekClient client() {
        DeepSeekClient client = new DeepSeekClient();
        WebSearchService webSearch = mock(WebSearchService.class);
        when(webSearch.isAvailable(org.mockito.ArgumentMatchers.any(ChatModelRuntime.class)))
                .thenReturn(true);
        when(webSearch.definitions()).thenReturn(Collections.<Map<String, Object>>emptyList());
        ReflectionTestUtils.setField(client, "webSearchService", webSearch);
        ReflectionTestUtils.setField(client, "objectMapper", new ObjectMapper());
        return client;
    }

    private ChatModelRuntime runtime(WebSearchMode mode) {
        ChatModelRuntime runtime = new ChatModelRuntime();
        runtime.setModel("qwen-plus");
        runtime.setBaseUrl("https://dashscope.aliyuncs.com/compatible-mode/v1");
        runtime.setChatPath("/chat/completions");
        runtime.setApiKey("test-key");
        runtime.setWebSearchEnabled(true);
        runtime.setWebSearchMode(mode);
        return runtime;
    }

    private List<ChatMessage> messages() {
        ChatMessage message = new ChatMessage();
        message.setRole("user");
        message.setContent("latest news");
        return Collections.singletonList(message);
    }
}
