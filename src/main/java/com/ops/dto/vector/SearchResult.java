package com.ops.dto.vector;

import lombok.Data;

/**
 * 向量搜索结果
 */
@Data
public class SearchResult {
    private String id;
    private String content;
    private float score;
    private String metadata;
}
