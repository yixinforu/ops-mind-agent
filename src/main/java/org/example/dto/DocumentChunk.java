package org.example.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * 文档分片
 */
@Setter
@Getter
public class DocumentChunk {

    // Getters and Setters
    /**
     * 分片内容
     */
    private String content;
    
    /**
     * 分片在原文档中的起始位置
     */
    private int startIndex;
    
    /**
     * 分片在原文档中的结束位置
     */
    private int endIndex;
    
    /**
     * 分片序号（从0开始）
     */
    private int chunkIndex;
    
    /**
     * 分片标题或上下文信息
     */
    private String title;

    public DocumentChunk() {
    }

    public DocumentChunk(String content, int startIndex, int endIndex, int chunkIndex) {
        this.content = content;
        this.startIndex = startIndex;
        this.endIndex = endIndex;
        this.chunkIndex = chunkIndex;
    }

    @Override
    public String toString() {
        return "DocumentChunk{" +
                "chunkIndex=" + chunkIndex +
                ", title='" + title + '\'' +
                ", contentLength=" + (content != null ? content.length() : 0) +
                ", startIndex=" + startIndex +
                ", endIndex=" + endIndex +
                '}';
    }
}
