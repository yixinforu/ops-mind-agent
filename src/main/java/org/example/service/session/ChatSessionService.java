package org.example.service.session;

import org.example.dto.chat.SessionInfoResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 会话管理服务
 */
@Service
public class ChatSessionService {

    private static final Logger logger = LoggerFactory.getLogger(ChatSessionService.class);

    private static final int MAX_WINDOW_SIZE = 6;

    private final Map<String, ChatSession> sessions = new ConcurrentHashMap<>();

    public ChatSession getOrCreateSession(String sessionId) {
        String effectiveSessionId = (sessionId == null || sessionId.isEmpty())
                ? UUID.randomUUID().toString()
                : sessionId;
        return sessions.computeIfAbsent(effectiveSessionId, ChatSession::new);
    }

    public List<Map<String, String>> getHistory(ChatSession session) {
        return session.getHistory();
    }

    public void addMessage(ChatSession session, String userQuestion, String aiAnswer) {
        session.addMessage(userQuestion, aiAnswer, MAX_WINDOW_SIZE);
        logger.debug("会话 {} 更新历史消息，当前消息对数: {}",
                session.getSessionId(), session.getMessagePairCount());
    }

    /**
     * 将 AI Ops 自动分析结果写入会话历史，便于后续 chat/chat_stream 继续追问时继承上下文。
     *
     * @param session 会话对象
     * @param summary AI Ops 摘要文本
     */
    public void addAiOpsSummary(ChatSession session, String summary) {
        session.addMessage("[系统任务] 执行 AI Ops 自动告警分析", summary, MAX_WINDOW_SIZE);
        logger.info("会话 {} 已写入 AI Ops 摘要上下文，当前消息对数: {}",
                session.getSessionId(), session.getMessagePairCount());
    }

    public boolean clearHistory(String sessionId) {
        ChatSession session = sessions.get(sessionId);
        if (session == null) {
            return false;
        }
        session.clearHistory();
        logger.info("会话 {} 历史消息已清空", sessionId);
        return true;
    }

    public Optional<SessionInfoResponse> getSessionInfo(String sessionId) {
        ChatSession session = sessions.get(sessionId);
        if (session == null) {
            return Optional.empty();
        }

        SessionInfoResponse response = new SessionInfoResponse();
        response.setSessionId(sessionId);
        response.setMessagePairCount(session.getMessagePairCount());
        response.setCreateTime(session.getCreateTime());
        return Optional.of(response);
    }
}
