package com.example.atw.client.service;

import com.example.atw.client.config.ClientModelProperties;
import com.example.atw.client.dto.ChatMessage;
import com.example.atw.client.dto.ChatRequest;
import com.example.atw.client.dto.ChatResponse;
import com.example.atw.client.dto.ChatToolCall;
import com.example.atw.client.dto.ChatUsage;
import com.example.atw.client.error.ClientApiException;
import com.example.atw.client.provider.DeepSeekClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatServiceToolLoopTest {

    @Test
    @SuppressWarnings("unchecked")
    void executesToolAndReturnsFinalAnswer() {
        ChatService service = new ChatService();
        DeepSeekClient deepSeek = mock(DeepSeekClient.class);
        WebSearchService webSearch = mock(WebSearchService.class);
        when(webSearch.isAvailable()).thenReturn(true);
        when(webSearch.execute("web_search", "{\"query\":\"latest news\"}"))
                .thenReturn("{\"results\":[]}");

        ChatToolCall call = toolCall("call_1", "web_search", "{\"query\":\"latest news\"}");
        when(deepSeek.complete(anyList(), eq(512), anyBoolean()))
                .thenReturn(completion("", call), completion("final answer", null));
        inject(service, deepSeek, webSearch);

        ChatResponse response = ReflectionTestUtils.invokeMethod(service, "completeWithTools",
                messages(), "req-test", 512, true);

        assertEquals("final answer", response.getChoices().get(0).getMessage().getContent());
        verify(webSearch).execute("web_search", "{\"query\":\"latest news\"}");

        ArgumentCaptor<List<ChatMessage>> captor = ArgumentCaptor.forClass(List.class);
        verify(deepSeek, times(2)).complete(captor.capture(), eq(512), anyBoolean());
        List<ChatMessage> secondRequest = captor.getAllValues().get(1);
        assertEquals("assistant", secondRequest.get(2).getRole());
        assertEquals("web_search", secondRequest.get(2).getToolCalls().get(0).getFunction().getName());
        assertEquals("tool", secondRequest.get(3).getRole());
        assertEquals("call_1", secondRequest.get(3).getToolCallId());
        assertEquals("{\"results\":[]}", secondRequest.get(3).getContent());
    }

    @Test
    void rejectsEmptyFinalAnswerAfterToolCall() {
        ChatService service = new ChatService();
        DeepSeekClient deepSeek = mock(DeepSeekClient.class);
        WebSearchService webSearch = mock(WebSearchService.class);
        when(webSearch.isAvailable()).thenReturn(true);
        when(webSearch.execute("web_search", "{}")).thenReturn("{\"results\":[]}");

        when(deepSeek.complete(anyList(), eq(512), anyBoolean()))
                .thenReturn(completion("", toolCall("call_1", "web_search", "{}")),
                        completion("", null));
        inject(service, deepSeek, webSearch);

        assertThrows(ClientApiException.class, () -> ReflectionTestUtils.invokeMethod(
                service, "completeWithTools", messages(), "req-test", 512, true));
    }

    @Test
    void doesNotExposeToolsForSummaryRequests() {
        ChatService service = new ChatService();
        WebSearchService webSearch = mock(WebSearchService.class);
        when(webSearch.isAvailable()).thenReturn(true);
        inject(service, mock(DeepSeekClient.class), webSearch);
        ChatRequest request = new ChatRequest();
        request.setPurpose("summary");

        Boolean allowed = ReflectionTestUtils.invokeMethod(service, "allowTools", request);

        assertFalse(allowed);
    }

    private void inject(ChatService service, DeepSeekClient deepSeek, WebSearchService webSearch) {
        ClientModelProperties properties = new ClientModelProperties();
        properties.getWebSearch().setMaxCallsPerRound(3);
        ReflectionTestUtils.setField(service, "properties", properties);
        ReflectionTestUtils.setField(service, "deepSeekClient", deepSeek);
        ReflectionTestUtils.setField(service, "webSearchService", webSearch);
        ReflectionTestUtils.setField(service, "objectMapper", new ObjectMapper());
    }

    private List<ChatMessage> messages() {
        List<ChatMessage> messages = new ArrayList<>();
        ChatMessage system = new ChatMessage();
        system.setRole("system");
        system.setContent("system");
        messages.add(system);
        ChatMessage user = new ChatMessage();
        user.setRole("user");
        user.setContent("latest news");
        messages.add(user);
        return messages;
    }

    private DeepSeekClient.Completion completion(String content, ChatToolCall toolCall) {
        DeepSeekClient.Completion completion = new DeepSeekClient.Completion();
        completion.setContent(content);
        completion.setFinishReason(toolCall == null ? "stop" : "tool_calls");
        completion.setUsage(usage());
        completion.setToolCalls(toolCall == null
                ? Collections.<ChatToolCall>emptyList()
                : Collections.singletonList(toolCall));
        return completion;
    }

    private ChatUsage usage() {
        ChatUsage usage = new ChatUsage();
        usage.setPromptTokens(10);
        usage.setCompletionTokens(5);
        usage.setTotalTokens(15);
        return usage;
    }

    private ChatToolCall toolCall(String id, String name, String arguments) {
        ChatToolCall toolCall = new ChatToolCall();
        toolCall.setId(id);
        ChatToolCall.Function function = new ChatToolCall.Function();
        function.setName(name);
        function.setArguments(arguments);
        toolCall.setFunction(function);
        return toolCall;
    }
}
