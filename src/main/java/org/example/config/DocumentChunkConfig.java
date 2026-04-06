package org.example.config;

import lombok.Getter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 文档分片配置
 */
@Getter
@Configuration
@ConfigurationProperties(prefix = "document.chunk")
public class DocumentChunkConfig {
    
    /**
     * 每个分片的最大字符数
     */
    private int maxSize = 800;
    
    /**
     * 分片之间的重叠字符数
     */
    private int overlap = 100;

    public void setMaxSize(int maxSize) {
        this.maxSize = maxSize;
    }

    public void setOverlap(int overlap) {
        this.overlap = overlap;
    }
}
