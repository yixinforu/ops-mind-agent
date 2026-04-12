package com.ops.agent.tool.model.logdto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 日志主题信息
 */
@Data
public class LogTopicInfo {
    @JsonProperty("topic_name")
    private String topicName;

    @JsonProperty("description")
    private String description;

    @JsonProperty("example_queries")
    private List<String> exampleQueries;

    @JsonProperty("related_alerts")
    private List<String> relatedAlerts;
}
