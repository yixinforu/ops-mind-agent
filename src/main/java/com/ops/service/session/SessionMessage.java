package com.ops.service.session;

import lombok.Data;

/**
 * Redis 会话消息对象
 */
@Data
public class SessionMessage {
    private String role;
    private String content;
}
