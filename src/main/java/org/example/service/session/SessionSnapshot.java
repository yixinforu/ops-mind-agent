package org.example.service.session;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Redis 会话快照对象
 */
@Data
public class SessionSnapshot {
    private String sessionId;
    private long createTime;
    private List<SessionMessage> messages = new ArrayList<>();
}
