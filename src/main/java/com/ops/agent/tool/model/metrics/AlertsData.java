package com.ops.agent.tool.model.metrics;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class AlertsData {
    private List<PrometheusAlert> alerts = new ArrayList<>();
}
