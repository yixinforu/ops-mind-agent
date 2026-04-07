package org.example.dto.chat;

import lombok.Data;

/**
 * 会话信息响应
 */
@Data
public class SessionInfoResponse {
    private String sessionId;
    private int messagePairCount;
    private long createTime;
}
