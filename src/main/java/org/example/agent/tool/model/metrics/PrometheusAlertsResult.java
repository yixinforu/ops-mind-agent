package org.example.agent.tool.model.metrics;

import lombok.Data;

/**
 * Prometheus 告警查询结果
 */
@Data
public class PrometheusAlertsResult {
    private String status;
    private AlertsData data;
    private String error;
    private String errorType;
}
