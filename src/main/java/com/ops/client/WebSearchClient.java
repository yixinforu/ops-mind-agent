package com.ops.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ops.dto.search.WebSearchItem;
import com.ops.dto.search.WebSearchResult;
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
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 第三方网络搜索客户端
 * 默认对接 Tavily 搜索接口，并在时间敏感问题上优先启用时间过滤。
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
        SearchOptions preferredOptions = resolveSearchOptions(query);
        return executeSearch(query, preferredOptions);
    }

    private WebSearchResult executeSearch(String query, SearchOptions options) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl))
                    .timeout(Duration.ofSeconds(Math.max(1L, timeoutSeconds)))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(buildRequestBody(query, options)))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return handleErrorResponse(query, options, response.statusCode(), response.body());
            }

            return parseResponse(query, response.body(), options);
        } catch (IOException e) {
            throw new IllegalStateException("搜索服务调用失败: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("搜索请求被中断", e);
        }
    }

    private WebSearchResult handleErrorResponse(String query, SearchOptions options, int statusCode, String responseBody) {
        logger.error("搜索服务返回异常状态码 - Status: {}, Query: {}, Topic: {}, TimeRange: {}, Body: {}",
                statusCode, query, options.topic, options.timeRange, trimForLog(responseBody));

        if (statusCode == 400 && options.temporalFilterApplied) {
            logger.warn("时间过滤搜索收到400，自动降级为无时间过滤搜索 - Query: {}", query);
            SearchOptions fallbackOptions = options.withoutTemporalFilters();
            WebSearchResult fallbackResult = executeSearch(query, fallbackOptions);
            if (fallbackResult != null) {
                fallbackResult.setMessage("时间过滤请求被搜索服务拒绝，已自动回退为常规搜索结果");
                return fallbackResult;
            }
        }

        throw new IllegalStateException("搜索服务返回异常状态码: " + statusCode);
    }

    private void validateConfig() {
        if (!"tavily".equalsIgnoreCase(provider)) {
            throw new IllegalStateException("当前仅支持 tavily 搜索供应商");
        }
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("未配置 search.api-key，网络搜索能力不可用");
        }
    }

    private String buildRequestBody(String query, SearchOptions options) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("api_key", apiKey);
        payload.put("query", query);
        payload.put("search_depth", "advanced");
        payload.put("max_results", Math.max(1, maxResults));
        payload.put("include_answer", false);
        payload.put("include_raw_content", false);
        payload.put("topic", options.topic);

        if (StringUtils.hasText(options.timeRange)) {
            payload.put("time_range", options.timeRange);
        }
        if (StringUtils.hasText(options.startDate)) {
            payload.put("start_date", options.startDate);
        }
        if (StringUtils.hasText(options.endDate)) {
            payload.put("end_date", options.endDate);
        }

        logger.info("发起网络搜索 - Query: {}, Topic: {}, TimeRange: {}, StartDate: {}, EndDate: {}",
                query, options.topic, options.timeRange, options.startDate, options.endDate);
        return objectMapper.writeValueAsString(payload);
    }

    private WebSearchResult parseResponse(String query, String responseBody, SearchOptions options) throws IOException {
        JsonNode root = objectMapper.readTree(responseBody);

        WebSearchResult result = new WebSearchResult();
        result.setQuery(query);
        result.setProvider(provider);
        result.setSearchedAt(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        result.setTemporalFilterApplied(options.temporalFilterApplied);
        result.setTopic(options.topic);
        result.setTimeRange(options.timeRange);
        result.setStartDate(options.startDate);
        result.setEndDate(options.endDate);

        JsonNode resultsNode = root.path("results");
        if (!resultsNode.isArray() || resultsNode.isEmpty()) {
            result.setStatus("no_results");
            result.setMessage(options.temporalFilterApplied
                    ? "未找到满足当前时间过滤条件的可靠公开结果"
                    : "No reliable public results found.");
            return result;
        }

        result.setStatus("success");
        result.setMessage(options.temporalFilterApplied
                ? "搜索成功（已启用最近时间过滤）"
                : "搜索成功");
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

        if (options.temporalFilterApplied) {
            result.getItems().sort(Comparator.comparing(this::resolvePublishedAtForSort).reversed());
        }

        logger.info("网络搜索完成 - Query: {}, 返回结果数: {}, TemporalFilterApplied: {}, Topic: {}, TimeRange: {}, StartDate: {}, EndDate: {}",
                query, result.getItems().size(), options.temporalFilterApplied, options.topic, options.timeRange, options.startDate, options.endDate);
        return result;
    }

    private SearchOptions resolveSearchOptions(String query) {
        SearchOptions options = SearchOptions.defaultOptions();
        if (!StringUtils.hasText(query)) {
            return options;
        }

        String normalizedQuery = query.trim().toLowerCase();
        if (!isTemporalQuery(normalizedQuery)) {
            return options;
        }

        options.temporalFilterApplied = true;
        options.topic = isNewsQuery(normalizedQuery) ? "news" : "general";
        options.timeRange = inferTimeRange(normalizedQuery);

        // 相对时间问题优先只使用 time_range，避免第三方接口对具体日期参数组合产生 400。
        options.startDate = null;
        options.endDate = null;
        return options;
    }

    private boolean isTemporalQuery(String query) {
        return containsAny(query,
                "最新", "最近", "近期", "当前", "目前", "今天", "今日", "当天", "昨天", "昨日",
                "刚刚", "实时", "本周", "这周", "本月", "今年", "newest", "latest", "recent",
                "current", "today", "yesterday", "this week", "this month", "this year", "now", "breaking");
    }

    private boolean isNewsQuery(String query) {
        return containsAny(query,
                "新闻", "资讯", "头条", "快讯", "动态", "消息", "报道", "发生了什么",
                "news", "headline", "breaking", "update");
    }

    private String inferTimeRange(String query) {
        if (containsAny(query, "今天", "今日", "当天", "today", "刚刚", "实时", "now")) {
            return "day";
        }
        if (containsAny(query, "昨天", "昨日", "yesterday", "本周", "这周", "本星期", "this week")) {
            return "week";
        }
        if (containsAny(query, "本月", "这个月", "this month", "近期", "最近", "recent")) {
            return "month";
        }
        if (containsAny(query, "今年", "本年", "this year")) {
            return "year";
        }
        if (containsAny(query, "最新", "latest", "newest", "当前", "目前", "current")) {
            return "month";
        }
        return "month";
    }

    private boolean containsAny(String query, String... keywords) {
        for (String keyword : keywords) {
            if (query.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private Instant resolvePublishedAtForSort(WebSearchItem item) {
        if (item == null || !StringUtils.hasText(item.getPublishedAt())) {
            return Instant.EPOCH;
        }

        String publishedAt = item.getPublishedAt().trim();
        try {
            return Instant.parse(publishedAt);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return OffsetDateTime.parse(publishedAt).toInstant();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(publishedAt, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                    .atZone(ZoneId.systemDefault())
                    .toInstant();
        } catch (DateTimeParseException ignored) {
        }
        try {
            return java.time.LocalDate.parse(publishedAt, DateTimeFormatter.ISO_LOCAL_DATE)
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant();
        } catch (DateTimeParseException ignored) {
            return Instant.EPOCH;
        }
    }

    private String trimSnippet(String snippet) {
        if (!StringUtils.hasText(snippet)) {
            return "";
        }
        String normalized = snippet.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 280 ? normalized : normalized.substring(0, 280) + "...";
    }

    private String trimForLog(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500) + "...";
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

    /**
     * 搜索请求选项。
     */
    private static class SearchOptions {

        private String topic = "general";

        private String timeRange;

        private String startDate;

        private String endDate;

        private boolean temporalFilterApplied;

        private static SearchOptions defaultOptions() {
            return new SearchOptions();
        }

        private SearchOptions withoutTemporalFilters() {
            SearchOptions options = new SearchOptions();
            options.topic = this.topic;
            options.timeRange = null;
            options.startDate = null;
            options.endDate = null;
            options.temporalFilterApplied = false;
            return options;
        }
    }
}
