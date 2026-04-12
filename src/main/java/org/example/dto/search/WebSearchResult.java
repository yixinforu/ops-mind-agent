package org.example.dto.search;

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

    private List<WebSearchItem> items = new ArrayList<>();
}
