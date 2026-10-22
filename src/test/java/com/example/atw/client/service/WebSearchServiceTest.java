package com.example.atw.client.service;

import com.example.atw.client.config.ClientModelProperties;
import com.example.atw.client.provider.HttpJsonClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebSearchServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void exposesSearchAndFetchTools() {
        WebSearchService service = service(mock(HttpJsonClient.class));

        List<Map<String, Object>> definitions = service.definitions();

        assertEquals(2, definitions.size());
        assertEquals("web_search", function(definitions.get(0)).get("name"));
        assertEquals("web_fetch", function(definitions.get(1)).get("name"));
    }

    @Test
    void parsesDeepSeekSearchResults() throws Exception {
        HttpJsonClient http = mock(HttpJsonClient.class);
        when(http.postJson(anyString(), anyMap(), any(), anyInt(), anyInt()))
                .thenReturn(new HttpJsonClient.JsonResult(200,
                        "{\"results\":["
                                + "{\"title\":\"Result A\",\"url\":\"https://example.com/a\","
                                + "\"snippet\":\"short\",\"summary\":\"long summary\","
                                + "\"site_name\":\"Example\",\"date\":\"2026-10-21T00:00:00Z\","
                                + "\"rerank_score\":2.1,\"authority\":3},"
                                + "{\"title\":\"Bad\",\"url\":\"javascript:alert(1)\",\"summary\":\"x\"}"
                                + "]}"));
        WebSearchService service = service(http);

        JsonNode result = objectMapper.readTree(service.execute("web_search",
                "{\"query\":\"latest news\"}"));

        assertEquals(1, result.path("results").size());
        assertEquals("Result A", result.path("results").path(0).path("title").asText());
        assertEquals("long summary", result.path("results").path(0).path("snippet").asText());
        assertEquals(3, result.path("results").path(0).path("authority").asInt());
    }

    @Test
    void fetchesMarkdownAndTruncatesContent() throws Exception {
        HttpJsonClient http = mock(HttpJsonClient.class);
        StringBuilder content = new StringBuilder("line one\\n\\n");
        for (int i = 0; i < 5000; i++) {
            content.append('a');
        }
        when(http.postJson(anyString(), anyMap(), any(), anyInt(), anyInt()))
                .thenReturn(new HttpJsonClient.JsonResult(200,
                        "{\"title\":\"Page\",\"content\":\"" + content + "\"}"));
        ClientModelProperties properties = properties();
        properties.getWebSearch().setMaxFetchChars(4000);
        WebSearchService service = service(properties, http);

        JsonNode result = objectMapper.readTree(service.execute("web_fetch",
                "{\"url\":\"https://example.com/page\"}"));

        assertEquals("Page", result.path("title").asText());
        assertTrue(result.path("content").asText().startsWith("line one\n\n"));
        assertEquals(4000, result.path("content").asText().length());
    }

    @Test
    void rejectsMissingQueryAndInvalidFetchUrl() throws Exception {
        WebSearchService service = service(mock(HttpJsonClient.class));

        assertTrue(objectMapper.readTree(service.execute("web_search", "{}")).has("error"));
        assertTrue(objectMapper.readTree(service.execute("web_fetch",
                "{\"url\":\"file:///etc/passwd\"}")).has("error"));
    }

    @Test
    void requiresDeepSeekApiKeyAndEnabledFlag() {
        ClientModelProperties properties = new ClientModelProperties();
        properties.getDeepseek().setApiKey("");
        WebSearchService service = service(properties, mock(HttpJsonClient.class));
        assertFalse(service.isAvailable());

        properties.getDeepseek().setApiKey("test-key");
        properties.getWebSearch().setEnabled(false);
        assertFalse(service.isAvailable());

        properties.getWebSearch().setEnabled(true);
        assertTrue(service.isAvailable());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> function(Map<String, Object> definition) {
        return (Map<String, Object>) definition.get("function");
    }

    private WebSearchService service(HttpJsonClient http) {
        return service(properties(), http);
    }

    private ClientModelProperties properties() {
        ClientModelProperties properties = new ClientModelProperties();
        properties.getDeepseek().setApiKey("test-key");
        properties.getDeepseek().setBaseUrl("https://api.deepseek.com");
        return properties;
    }

    private WebSearchService service(ClientModelProperties properties, HttpJsonClient http) {
        WebSearchService service = new WebSearchService();
        ReflectionTestUtils.setField(service, "properties", properties);
        ReflectionTestUtils.setField(service, "http", http);
        ReflectionTestUtils.setField(service, "objectMapper", objectMapper);
        return service;
    }
}
