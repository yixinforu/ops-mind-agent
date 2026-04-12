package com.ops.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ops.client.WebSearchClient;
import com.ops.dto.search.WebSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 网络搜索工具
 * 负责查询公开网络信息，并以结构化 JSON 返回给 Agent。
 */
@Component
public class WebSearchTools {

    private static final Logger logger = LoggerFactory.getLogger(WebSearchTools.class);

    private final WebSearchClient webSearchClient;

    private final ObjectMapper objectMapper;

    @Autowired
    public WebSearchTools(WebSearchClient webSearchClient, ObjectMapper objectMapper) {
        this.webSearchClient = webSearchClient;
        this.objectMapper = objectMapper;
    }

    /**
     * 查询公开网络资料。
     */
    @Tool(description = "Search public web information for recent updates, official announcements, documentation, news, and other external references. " +
            "Use this tool when the user asks about the latest public information, official websites, releases, announcements, or external materials. " +
            "Always summarize the results and keep the source links.")
    public String searchWeb(
            @ToolParam(description = "Search query for the latest public information, official documentation, announcements, or news")
            String query) {
        try {
            WebSearchResult result = webSearchClient.search(query);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception e) {
            logger.error("[工具错误] searchWeb 执行失败 - Query: {}", query, e);
            return buildErrorResult(e.getMessage(), query);
        }
    }

    private String buildErrorResult(String message, String query) {
        try {
            WebSearchResult result = new WebSearchResult();
            result.setStatus("error");
            result.setProvider("tavily");
            result.setQuery(query);
            result.setMessage(message);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(result);
        } catch (Exception ex) {
            return String.format("{\"status\":\"error\",\"query\":\"%s\",\"message\":\"%s\"}", query, message);
        }
    }
}
