package com.ops.dto.search;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 网络搜索工具统一返回结果
 */
@Data
public class WebSearchResult {

    private String status;

    private String provider;

    private String query;

    private String message;

    /**
     * 本次搜索的时间上下文，便于模型理解“今天/最新”类问题的过滤范围。
     */
    private String searchedAt;

    /**
     * 是否对时间敏感问题启用了最近时间过滤。
     */
    private boolean temporalFilterApplied;

    /**
     * 本次搜索使用的 Tavily topic。
     */
    private String topic;

    /**
     * 本次搜索使用的时间范围。
     */
    private String timeRange;

    /**
     * 本次搜索过滤起始日期。
     */
    private String startDate;

    /**
     * 本次搜索过滤结束日期。
     */
    private String endDate;

    private List<WebSearchItem> items = new ArrayList<>();
}
