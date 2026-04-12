package org.example.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dto.search.WebSearchItem;
import org.example.dto.search.WebSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 第三方网络搜索客户端
 * 一期默认对接 Tavily 搜索接口。
 */
@Component
public class WebSearchClient {

    private static final Logger logger = LoggerFactory.getLogger(WebSearchClient.class);

    private final ObjectMapper objectMapper;

    private HttpClient httpClient;

    @Value("${search.provider:tavily}")
    private String provider;

    @Value("${search.api-key:}")
    private String apiKey;

    @Value("${search.base-url:https://api.tavily.com/search}")
    private String baseUrl;

    @Value("${search.max-results:5}")
    private int maxResults;

    @Value("${search.timeout-seconds:15}")
    private long timeoutSeconds;

    @Autowired
    public WebSearchClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @jakarta.annotation.PostConstruct
    public void init() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1L, timeoutSeconds)))
                .build();
    }

    /**
     * 执行网络搜索并返回标准化结果。
     */
    public WebSearchResult search(String query) {
        validateConfig();

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .timeout(Duration.ofSeconds(Math.max(1L, timeoutSeconds)))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(query)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("搜索服务返回异常状态码: " + response.statusCode());
            }

            return parseResponse(query, response.body());
        } catch (IOException e) {
            throw new IllegalStateException("搜索服务调用失败: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("搜索请求被中断", e);
        }
    }

    private void validateConfig() {
        if (!"tavily".equalsIgnoreCase(provider)) {
            throw new IllegalStateException("当前仅支持 tavily 搜索供应商");
        }
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("未配置 search.api-key，网络搜索能力不可用");
        }
    }

    private String buildRequestBody(String query) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("api_key", apiKey);
        payload.put("query", query);
        payload.put("search_depth", "advanced");
        payload.put("max_results", Math.max(1, maxResults));
        payload.put("include_answer", false);
        payload.put("include_raw_content", false);
        return objectMapper.writeValueAsString(payload);
    }

    private WebSearchResult parseResponse(String query, String responseBody) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);

        WebSearchResult result = new WebSearchResult();
        result.setQuery(query);
        result.setProvider(provider);

        JsonNode resultsNode = root.path("results");
        if (!resultsNode.isArray() || resultsNode.isEmpty()) {
            result.setStatus("no_results");
            result.setMessage("No reliable public results found.");
            return result;
        }

        result.setStatus("success");
        result.setMessage("搜索成功");
        Iterator<JsonNode> iterator = resultsNode.elements();
        while (iterator.hasNext()) {
            JsonNode itemNode = iterator.next();
            WebSearchItem item = new WebSearchItem();
            item.setTitle(itemNode.path("title").asText(""));
            item.setUrl(itemNode.path("url").asText(""));
            item.setSnippet(trimSnippet(itemNode.path("content").asText("")));
            item.setPublishedAt(itemNode.path("published_date").asText(""));
            item.setSource(resolveSource(itemNode));
            result.getItems().add(item);
        }

        logger.info("网络搜索完成 - Query: {}, 返回结果数: {}", query, result.getItems().size());
        return result;
    }

    private String trimSnippet(String snippet) {
        if (!StringUtils.hasText(snippet)) {
            return "";
        }
        String normalized = snippet.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 280 ? normalized : normalized.substring(0, 280) + "...";
    }

    private String resolveSource(JsonNode itemNode) {
        String siteName = itemNode.path("site_name").asText("");
        if (StringUtils.hasText(siteName)) {
            return siteName;
        }

        String url = itemNode.path("url").asText("");
        if (!StringUtils.hasText(url)) {
            return "";
        }

        try {
            URI uri = new URI(url);
            return uri.getHost() == null ? "" : uri.getHost();
        } catch (URISyntaxException e) {
            logger.debug("解析来源域名失败 - Url: {}", url, e);
            return "";
        }
    }
}
