package org.example.dto.search;

import lombok.Data;

/**
 * 单条网络搜索结果
 */
@Data
public class WebSearchItem {

    private String title;

    private String url;

    private String snippet;

    private String source;

    private String publishedAt;
}
