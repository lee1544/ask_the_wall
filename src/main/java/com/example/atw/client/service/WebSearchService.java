package com.example.atw.client.service;

import com.example.atw.client.config.ChatModelRuntime;
import com.example.atw.client.config.ClientModelProperties;
import com.example.atw.client.dto.WebSearchResult;
import com.example.atw.client.error.ClientApiException;
import com.example.atw.client.provider.HttpJsonClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WebSearchService {

    private static final Logger log = LoggerFactory.getLogger(WebSearchService.class);
    private static final String TOOL_SEARCH = "web_search";
    private static final String TOOL_FETCH = "web_fetch";

    @Autowired
    private ClientModelProperties properties;

    @Autowired
    private HttpJsonClient http;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ChatModelConfigService modelConfigService;

    public boolean isAvailable() {
        return isAvailable(modelConfigService.runtime());
    }

    public boolean isAvailable(ChatModelRuntime runtime) {
        return properties.getWebSearch().isEnabled()
                && runtime.isWebSearchEnabled()
                && runtime.hasApiKey();
    }

    public List<Map<String, Object>> definitions() {
        return Arrays.asList(searchDefinition(), fetchDefinition());
    }

    public String execute(String name, String arguments) {
        return execute(name, arguments, modelConfigService.runtime());
    }

    public String execute(String name, String arguments, ChatModelRuntime runtime) {
        if (!isAvailable(runtime)) {
            return error("联网搜索未配置");
        }
        if (TOOL_SEARCH.equals(name)) {
            return executeSearch(arguments, runtime);
        }
        if (TOOL_FETCH.equals(name)) {
            return executeFetch(arguments, runtime);
        }
        return error("不支持的工具");
    }

    private Map<String, Object> searchDefinition() {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("type", "string");
        query.put("description", "搜索关键词，最多 128 字符。支持 site:、-site:、引号短语、after:、before: 等操作符。");

        Map<String, Object> count = new LinkedHashMap<>();
        count.put("type", "integer");
        count.put("description", "返回结果数量，1-" + properties.getWebSearch().getMaxResults() + "。");
        count.put("minimum", 1);
        count.put("maximum", properties.getWebSearch().getMaxResults());

        Map<String, Object> oriQuestion = new LinkedHashMap<>();
        oriQuestion.put("type", "string");
        oriQuestion.put("description", "触发本次搜索的用户原始问题，最多 128 字符，可选。");
        oriQuestion.put("maxLength", 128);

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        Map<String, Object> propertiesMap = new LinkedHashMap<>();
        propertiesMap.put("query", query);
        propertiesMap.put("count", count);
        propertiesMap.put("ori_question", oriQuestion);
        params.put("properties", propertiesMap);
        params.put("required", Collections.singletonList("query"));
        params.put("additionalProperties", false);

        return tool(TOOL_SEARCH,
                "搜索互联网获取实时信息。仅在问题涉及最新新闻、当前价格、天气、体育、政策、产品发布等时效性信息，"
                        + "或需要外部事实核验时调用。不要用于常识、创作、翻译或用户已提供完整资料的场景。",
                params);
    }

    private Map<String, Object> fetchDefinition() {
        Map<String, Object> url = new LinkedHashMap<>();
        url.put("type", "string");
        url.put("description", "要抓取正文的 HTTP/HTTPS 网页地址，通常来自 web_search 的结果。");

        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");
        params.put("properties", Collections.singletonMap("url", url));
        params.put("required", Collections.singletonList("url"));
        params.put("additionalProperties", false);

        return tool(TOOL_FETCH,
                "抓取指定网页并返回 Markdown 正文。仅在搜索摘要不足以回答、需要核对原文细节时调用；"
                        + "优先抓取权威来源，单次只抓一个 URL。",
                params);
    }

    private Map<String, Object> tool(String name, String description, Map<String, Object> parameters) {
        Map<String, Object> function = new LinkedHashMap<>();
        function.put("name", name);
        function.put("description", description);
        function.put("parameters", parameters);

        Map<String, Object> tool = new LinkedHashMap<>();
        tool.put("type", "function");
        tool.put("function", function);
        return tool;
    }

    private String executeSearch(String arguments, ChatModelRuntime runtime) {
        try {
            JsonNode args = objectMapper.readTree(arguments == null || arguments.trim().isEmpty()
                    ? "{}" : arguments);
            String query = trim(args.path("query").asText(""), 128);
            if (query.isEmpty()) {
                return error("query 不能为空");
            }
            int maxResults = args.path("count").asInt(properties.getWebSearch().getMaxResults());
            maxResults = Math.max(1, Math.min(maxResults, properties.getWebSearch().getMaxResults()));

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("query", query);
            body.put("count", maxResults);
            String oriQuestion = trim(args.path("ori_question").asText(""), 128);
            if (!oriQuestion.isEmpty()) {
                body.put("ori_question", oriQuestion);
            }

            HttpJsonClient.JsonResult result = http.postJson(
                    toolEndpoint(runtime, "/tools/search"), headers(runtime), body,
                    properties.getWebSearch().getConnectTimeoutMs(),
                    properties.getWebSearch().getReadTimeoutMs());
            checkStatus(result);
            JsonNode array = objectMapper.readTree(result.getBody()).path("results");
            List<WebSearchResult> results = new ArrayList<>();
            for (JsonNode node : array) {
                if (results.size() >= maxResults) {
                    break;
                }
                WebSearchResult item = parseSearchResult(node);
                if (item != null) {
                    results.add(item);
                }
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("query", query);
            payload.put("results", results);
            return objectMapper.writeValueAsString(payload);
        } catch (ClientApiException e) {
            log.warn("DeepSeek 搜索调用失败 - code={}, message={}", e.getCode(), e.getMessage());
            return error("联网搜索暂不可用");
        } catch (Exception e) {
            log.warn("DeepSeek 搜索处理失败", e);
            return error("联网搜索暂不可用");
        }
    }

    private String executeFetch(String arguments, ChatModelRuntime runtime) {
        try {
            JsonNode args = objectMapper.readTree(arguments == null || arguments.trim().isEmpty()
                    ? "{}" : arguments);
            String url = args.path("url").asText("").trim();
            if (!isFetchableUrl(url)) {
                return error("url 必须是有效的 HTTP/HTTPS 地址");
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("url", url);
            HttpJsonClient.JsonResult result = http.postJson(
                    toolEndpoint(runtime, "/tools/fetch"), headers(runtime), body,
                    properties.getWebSearch().getConnectTimeoutMs(),
                    properties.getWebSearch().getReadTimeoutMs());
            checkStatus(result);

            JsonNode root = objectMapper.readTree(result.getBody());
            String title = trim(root.path("title").asText(""), 200);
            String content = trimContent(root.path("content").asText(""),
                    properties.getWebSearch().getMaxFetchChars());
            if (content.isEmpty()) {
                return error("网页正文为空");
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("url", url);
            payload.put("title", title);
            payload.put("content", content);
            return objectMapper.writeValueAsString(payload);
        } catch (ClientApiException e) {
            log.warn("DeepSeek 网页抓取失败 - code={}, message={}", e.getCode(), e.getMessage());
            return error("网页抓取暂不可用");
        } catch (Exception e) {
            log.warn("DeepSeek 网页抓取处理失败", e);
            return error("网页抓取暂不可用");
        }
    }

    private WebSearchResult parseSearchResult(JsonNode node) {
        String url = text(node, "url");
        if (!isHttpUrl(url)) {
            return null;
        }
        WebSearchResult result = new WebSearchResult();
        result.setTitle(trim(text(node, "title"), 200));
        result.setUrl(url);
        result.setSnippet(trim(firstText(node, "summary", "snippet"),
                properties.getWebSearch().getMaxSummaryChars()));
        result.setPublishedAt(text(node, "date"));
        result.setSiteName(trim(text(node, "site_name"), 100));
        if (node.has("rerank_score") && !node.get("rerank_score").isNull()) {
            result.setScore(node.get("rerank_score").asDouble());
        }
        if (node.has("authority") && !node.get("authority").isNull()) {
            result.setAuthority(node.get("authority").asInt());
        }
        return result;
    }

    private Map<String, String> headers(ChatModelRuntime runtime) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer " + runtime.getApiKey());
        return headers;
    }

    private String toolEndpoint(ChatModelRuntime runtime, String path) {
        String base = trimBase(runtime.getBaseUrl());
        if (base.endsWith("/v1")) {
            return base + path;
        }
        return base + "/v1" + path;
    }

    private void checkStatus(HttpJsonClient.JsonResult result) {
        if (result.getStatus() >= 400) {
            throw new ClientApiException(503, "WEB_SEARCH_ERROR", "DeepSeek 搜索服务返回异常");
        }
    }

    private boolean isFetchableUrl(String value) {
        if (!isHttpUrl(value) || value.length() > 8192) {
            return false;
        }
        try {
            URI uri = new URI(value);
            return uri.getHost() != null && uri.getUserInfo() == null;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isHttpUrl(String value) {
        return value != null && (value.startsWith("http://") || value.startsWith("https://"));
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText("");
    }

    private String firstText(JsonNode node, String first, String second) {
        String value = text(node, first);
        return value.isEmpty() ? text(node, second) : value;
    }

    private String trim(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        String normalized = value.replaceAll("\\s+", " ").trim();
        if (maxChars > 0 && normalized.length() > maxChars) {
            return normalized.substring(0, maxChars);
        }
        return normalized;
    }

    private String trimContent(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        String normalized = value.trim();
        if (maxChars > 0 && normalized.length() > maxChars) {
            return normalized.substring(0, maxChars);
        }
        return normalized;
    }

    private String trimBase(String value) {
        String base = value == null ? "" : value.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String error(String message) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("error", message);
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return "{\"error\":\"联网搜索暂不可用\"}";
        }
    }
}
