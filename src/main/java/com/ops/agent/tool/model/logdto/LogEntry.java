package com.ops.agent.tool.model.logdto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.Map;

/**
 * 日志条目
 */
@Data
public class LogEntry {
    @JsonProperty("timestamp")
    private String timestamp;

    @JsonProperty("level")
    private String level;

    @JsonProperty("service")
    private String service;

    @JsonProperty("instance")
    private String instance;

    @JsonProperty("message")
    private String message;

    @JsonProperty("metrics")
    private Map<String, String> metrics;
}
