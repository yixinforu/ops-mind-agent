package org.example.agent.tool.model.metrics;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * 简化的告警信息
 */
@Data
public class SimplifiedAlert {
    @JsonProperty("alert_name")
    private String alertName;

    @JsonProperty("description")
    private String description;

    @JsonProperty("state")
    private String state;

    @JsonProperty("active_at")
    private String activeAt;

    @JsonProperty("duration")
    private String duration;
}
