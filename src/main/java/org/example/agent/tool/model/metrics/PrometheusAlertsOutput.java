package org.example.agent.tool.model.metrics;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * 告警查询输出
 */
@Data
public class PrometheusAlertsOutput {
    @JsonProperty("success")
    private boolean success;

    @JsonProperty("alerts")
    private List<SimplifiedAlert> alerts;

    @JsonProperty("message")
    private String message;

    @JsonProperty("error")
    private String error;
}
