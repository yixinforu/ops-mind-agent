package org.example.agent.tool.model.logdto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 日志主题列表输出
 */
@Data
public class LogTopicsOutput {
    @JsonProperty("success")
    private boolean success;

    @JsonProperty("topics")
    private List<LogTopicInfo> topics;

    @JsonProperty("available_regions")
    private List<String> availableRegions;

    @JsonProperty("default_region")
    private String defaultRegion;

    @JsonProperty("message")
    private String message;
}
