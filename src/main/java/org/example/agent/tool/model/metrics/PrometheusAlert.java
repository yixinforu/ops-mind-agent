package org.example.agent.tool.model.metrics;

import lombok.Data;

import java.util.Map;

/**
 * Prometheus 告警信息结构
 */
@Data
public class PrometheusAlert {
    private Map<String, String> labels;
    private Map<String, String> annotations;
    private String state;
    private String activeAt;
    private String value;
}
