package com.example.atw.client.service;

import com.example.atw.client.config.ChatModelRuntime;
import com.example.atw.client.config.ClientModelProperties;
import com.example.atw.client.config.WebSearchMode;
import com.example.atw.client.dto.ChatModelConfigRequest;
import com.example.atw.client.entity.ChatModelConfig;
import com.example.atw.client.repository.ChatModelConfigRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatModelConfigServiceTest {

    @Test
    void fallsBackToEnvironmentPropertiesWhenNoActiveConfig() {
        ClientModelProperties properties = new ClientModelProperties();
        properties.getDeepseek().setBaseUrl("https://api.deepseek.com/");
        properties.getDeepseek().setModel("deepseek-chat");
        properties.getDeepseek().setApiKey("properties-key");
        ChatModelConfigRepository repository = mock(ChatModelConfigRepository.class);
        when(repository.findFirstByActiveTrue()).thenReturn(Optional.empty());

        ChatModelConfigService service = service(repository, properties);
        doReturn(null).when(service).environmentVariable("DEEPSEEK_API_KEY");

        ChatModelRuntime runtime = service.runtime();

        assertTrue(runtime.isFallback());
        assertEquals("https://api.deepseek.com", runtime.getBaseUrl());
        assertEquals("deepseek-chat", runtime.getModel());
        assertEquals("properties-key", runtime.getApiKey());
        assertEquals(properties.getChat().getSpaceId(), runtime.getPolicyVersion());
    }

    @Test
    void resolvesBailianApiKeyFromSpringPropertiesAsFallback() {
        ClientModelProperties properties = new ClientModelProperties();
        properties.getBailian().setApiKey("bailian-properties-key");
        ChatModelConfig config = config(3L, true, 1);
        config.setApiKeyEnv("BAILIAN_API_KEY");
        ChatModelConfigRepository repository = mock(ChatModelConfigRepository.class);
        when(repository.findFirstByActiveTrue()).thenReturn(Optional.of(config));

        ChatModelConfigService service = service(repository, properties);
        doReturn(null).when(service).environmentVariable("BAILIAN_API_KEY");

        ChatModelRuntime runtime = service.runtime();

        assertEquals("bailian-properties-key", runtime.getApiKey());
    }

    @Test
    void mapsActiveConfigAndResolvesApiKeyFromEnvironment() {
        ClientModelProperties properties = new ClientModelProperties();
        properties.getDeepseek().setApiKey("properties-key");
        ChatModelConfig config = new ChatModelConfig();
        config.setId(7L);
        config.setConfigName("OpenAI 生产");
        config.setBaseUrl("https://api.openai.com/v1/");
        config.setModelName("gpt-4.1-mini");
        config.setApiKeyEnv("OPENAI_API_KEY");
        config.setChatPath("chat/completions");
        config.setContextBudget(128000);
        config.setMaxOutputTokens(4096);
        config.setConnectTimeoutMs(5000);
        config.setReadTimeoutMs(120000);
        config.setWebSearchEnabled(false);
        config.setActive(true);
        config.setRevision(3);
        config.setUpdateTime(LocalDateTime.of(2026, 10, 23, 10, 0));
        ChatModelConfigRepository repository = mock(ChatModelConfigRepository.class);
        when(repository.findFirstByActiveTrue()).thenReturn(Optional.of(config));

        ChatModelConfigService service = service(repository, properties);
        doReturn("env-key").when(service).environmentVariable("OPENAI_API_KEY");

        ChatModelRuntime runtime = service.runtime();

        assertFalse(runtime.isFallback());
        assertEquals(Long.valueOf(7L), runtime.getConfigId());
        assertEquals("https://api.openai.com/v1", runtime.getBaseUrl());
        assertEquals("gpt-4.1-mini", runtime.getModel());
        assertEquals("/chat/completions", runtime.getChatPath());
        assertEquals("env-key", runtime.getApiKey());
        assertEquals(128000, runtime.getContextBudget());
        assertEquals(4096, runtime.getMaxOutputTokens());
        assertFalse(runtime.isWebSearchEnabled());
        assertEquals("chat-model-7-3", runtime.getPolicyVersion());
    }

    @Test
    void mapsQwenWebSearchMode() {
        ClientModelProperties properties = new ClientModelProperties();
        ChatModelConfig config = config(8L, true, 2);
        config.setWebSearchEnabled(true);
        config.setWebSearchMode(WebSearchMode.QWEN_ENABLE_SEARCH);
        ChatModelConfigRepository repository = mock(ChatModelConfigRepository.class);
        when(repository.findFirstByActiveTrue()).thenReturn(Optional.of(config));

        ChatModelConfigService service = service(repository, properties);
        doReturn("env-key").when(service).environmentVariable("OPENAI_API_KEY");

        ChatModelRuntime runtime = service.runtime();

        assertTrue(runtime.isWebSearchEnabled());
        assertTrue(runtime.usesQwenWebSearch());
        assertFalse(runtime.usesDeepSeekWebSearch());
    }

    @Test
    void createsConfigInactiveAndVersionsItFromRevisionOne() {
        ClientModelProperties properties = new ClientModelProperties();
        ChatModelConfigRepository repository = mock(ChatModelConfigRepository.class);
        when(repository.findByConfigName("OpenAI 生产")).thenReturn(Optional.empty());
        when(repository.save(any(ChatModelConfig.class))).thenAnswer(invocation -> {
            ChatModelConfig saved = invocation.getArgument(0);
            saved.setId(9L);
            return saved;
        });
        ChatModelConfigService service = service(repository, properties);
        doReturn(null).when(service).environmentVariable("OPENAI_API_KEY");

        ChatModelConfig created = service.create(request());

        assertFalse(created.getActive());
        assertEquals(Integer.valueOf(1), created.getRevision());
        assertEquals("chat-model-9-1", created.getPolicyVersion());
        assertEquals(WebSearchMode.DEEPSEEK_TOOLS, created.getWebSearchMode());
        assertFalse(created.getApiKeyConfigured());
    }

    @Test
    void activationDeactivatesOthersAndBumpsRevision() {
        ClientModelProperties properties = new ClientModelProperties();
        ChatModelConfig oldActive = config(1L, true, 2);
        ChatModelConfig target = config(2L, false, 4);
        ChatModelConfigRepository repository = mock(ChatModelConfigRepository.class);
        when(repository.findById(2L)).thenReturn(Optional.of(target));
        when(repository.findAll()).thenReturn(Arrays.asList(oldActive, target));
        ChatModelConfigService service = service(repository, properties);
        doReturn(null).when(service).environmentVariable("OPENAI_API_KEY");

        ChatModelConfig activated = service.activate(2L);

        assertFalse(oldActive.getActive());
        assertTrue(target.getActive());
        assertEquals(Integer.valueOf(5), target.getRevision());
        assertEquals("chat-model-2-5", activated.getPolicyVersion());
        verify(repository).saveAll(anyList());
    }

    private ChatModelConfigRequest request() {
        ChatModelConfigRequest request = new ChatModelConfigRequest();
        request.setConfigName("OpenAI 生产");
        request.setBaseUrl("https://api.openai.com/v1/");
        request.setModelName("gpt-4.1-mini");
        request.setApiKeyEnv("OPENAI_API_KEY");
        request.setChatPath("/chat/completions");
        request.setContextBudget(128000);
        request.setMaxOutputTokens(4096);
        request.setConnectTimeoutMs(5000);
        request.setReadTimeoutMs(120000);
        request.setWebSearchEnabled(false);
        return request;
    }

    private ChatModelConfig config(Long id, boolean active, int revision) {
        ChatModelConfig config = new ChatModelConfig();
        config.setId(id);
        config.setConfigName("配置 " + id);
        config.setBaseUrl("https://api.openai.com/v1");
        config.setModelName("gpt-4.1-mini");
        config.setApiKeyEnv("OPENAI_API_KEY");
        config.setChatPath("/chat/completions");
        config.setContextBudget(128000);
        config.setMaxOutputTokens(4096);
        config.setConnectTimeoutMs(5000);
        config.setReadTimeoutMs(120000);
        config.setWebSearchEnabled(false);
        config.setWebSearchMode(WebSearchMode.DEEPSEEK_TOOLS);
        config.setActive(active);
        config.setRevision(revision);
        return config;
    }

    private ChatModelConfigService service(ChatModelConfigRepository repository,
                                           ClientModelProperties properties) {
        ChatModelConfigService service = spy(new ChatModelConfigService());
        ReflectionTestUtils.setField(service, "repository", repository);
        ReflectionTestUtils.setField(service, "properties", properties);
        return service;
    }
}
