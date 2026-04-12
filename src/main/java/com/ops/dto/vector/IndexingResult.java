package com.ops.dto.vector;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 索引结果
 */
@Data
public class IndexingResult {
    private boolean success;
    private String directoryPath;
    private int totalFiles;
    private int successCount;
    private int failCount;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String errorMessage;
    private final Map<String, String> failedFiles = new HashMap<>();

    public void incrementSuccessCount() {
        this.successCount++;
    }

    public void incrementFailCount() {
        this.failCount++;
    }

    public long getDurationMs() {
        if (startTime != null && endTime != null) {
            return java.time.Duration.between(startTime, endTime).toMillis();
        }
        return 0;
    }

    public void addFailedFile(String filePath, String error) {
        this.failedFiles.put(filePath, error);
    }
}
