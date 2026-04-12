package com.ops.agent.tool.model.logdto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 日志查询输出
 */
@Data
public class QueryLogsOutput {
    @JsonProperty("success")
    private boolean success;

    @JsonProperty("region")
    private String region;

    @JsonProperty("log_topic")
    private String logTopic;

    @JsonProperty("query")
    private String query;

    @JsonProperty("logs")
    private List<LogEntry> logs;

    @JsonProperty("total")
    private int total;

    @JsonProperty("message")
    private String message;
}
