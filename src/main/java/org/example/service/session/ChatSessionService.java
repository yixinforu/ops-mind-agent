package org.example.service.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.dto.chat.SessionInfoResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 会话管理服务（Redis 持久化 + 内存降级）
 */
@Service
public class ChatSessionService {

    private static final Logger logger = LoggerFactory.getLogger(ChatSessionService.class);
    private static final String SESSION_KEY_PREFIX = "chat:session:";
    private static final String UI_HISTORY_KEY = "chat:ui:histories";

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${chat.session.max-window-size:6}")
    private int maxWindowSize;

    @Value("${chat.session.ttl-minutes:10080}")
    private long sessionTtlMinutes;

    private final Map<String, ChatSession> fallbackSessions = new ConcurrentHashMap<>();
    private volatile List<Map<String, Object>> fallbackUiHistories = new ArrayList<>();

    public ChatSession getOrCreateSession(String sessionId) {
        String effectiveSessionId = (sessionId == null || sessionId.isEmpty())
                ? UUID.randomUUID().toString()
                : sessionId;

        ChatSession fallbackSession = fallbackSessions.get(effectiveSessionId);
        if (fallbackSession != null) {
            // 优先复用降级期间的内存会话，避免 Redis 恢复后上下文断裂。
            persistSession(fallbackSession, "恢复会话到 Redis");
            return fallbackSession;
        }

        if (redisTemplate != null) {
            try {
                Optional<ChatSession> redisSessionOptional = loadSessionFromRedis(effectiveSessionId);
                if (redisSessionOptional.isPresent()) {
                    return redisSessionOptional.get();
                }

                ChatSession newSession = new ChatSession(effectiveSessionId);
                saveSessionToRedis(newSession);
                return newSession;
            } catch (Exception e) {
                logger.warn("Redis 会话读取/创建失败，降级内存模式 - SessionId: {}, error: {}",
                        effectiveSessionId, e.getMessage());
            }
        }

        return fallbackSessions.computeIfAbsent(effectiveSessionId, ChatSession::new);
    }

    public List<Map<String, String>> getHistory(ChatSession session) {
        return session.getRecentHistory(maxWindowSize);
    }

    /**
     * 获取会话完整消息历史（用于前端展示）
     */
    public Optional<List<Map<String, String>>> getSessionMessages(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return Optional.empty();
        }

        if (redisTemplate != null) {
            try {
                Optional<ChatSession> redisSessionOptional = loadSessionFromRedis(sessionId);
                if (redisSessionOptional.isPresent()) {
                    return Optional.of(redisSessionOptional.get().getHistory());
                }
            } catch (Exception e) {
                logger.warn("Redis 查询会话消息失败，降级内存模式 - SessionId: {}, error: {}", sessionId, e.getMessage());
            }
        }

        ChatSession fallbackSession = fallbackSessions.get(sessionId);
        if (fallbackSession == null) {
            return Optional.empty();
        }
        return Optional.of(fallbackSession.getHistory());
    }

    public void addMessage(ChatSession session, String userQuestion, String aiAnswer) {
        session.addMessage(userQuestion, aiAnswer, maxWindowSize);
        persistSession(session, "更新对话消息");
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
        session.addMessage("[系统任务] 执行 AI Ops 自动告警分析", summary, maxWindowSize);
        persistSession(session, "写入 AI Ops 摘要");
        logger.info("会话 {} 已写入 AI Ops 摘要上下文，当前消息对数: {}",
                session.getSessionId(), session.getMessagePairCount());
    }

    public boolean clearHistory(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return false;
        }

        boolean cleared = false;

        if (redisTemplate != null) {
            try {
                Boolean deleted = redisTemplate.delete(buildSessionKey(sessionId));
                if (Boolean.TRUE.equals(deleted)) {
                    cleared = true;
                }
            } catch (Exception e) {
                logger.warn("Redis 清空会话失败，尝试内存兜底 - SessionId: {}, error: {}", sessionId, e.getMessage());
            }
        }

        ChatSession fallbackSession = fallbackSessions.remove(sessionId);
        if (fallbackSession != null) {
            fallbackSession.clearHistory();
            cleared = true;
        }

        if (cleared) {
            removeUiHistory(sessionId);
            logger.info("会话 {} 历史消息已清空", sessionId);
        }
        return cleared;
    }

    public Optional<SessionInfoResponse> getSessionInfo(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) {
            return Optional.empty();
        }

        if (redisTemplate != null) {
            try {
                Optional<ChatSession> redisSessionOptional = loadSessionFromRedis(sessionId);
                if (redisSessionOptional.isPresent()) {
                    return Optional.of(buildSessionInfoResponse(redisSessionOptional.get()));
                }
            } catch (Exception e) {
                logger.warn("Redis 查询会话信息失败，降级内存模式 - SessionId: {}, error: {}", sessionId, e.getMessage());
            }
        }

        ChatSession fallbackSession = fallbackSessions.get(sessionId);
        if (fallbackSession == null) {
            return Optional.empty();
        }
        return Optional.of(buildSessionInfoResponse(fallbackSession));
    }

    /**
     * 保存前端“近期对话”列表到 Redis，支持跨浏览器读取。
     */
    public void saveUiChatHistories(List<Map<String, Object>> histories) {
        List<Map<String, Object>> normalized = normalizeUiHistories(histories);
        if (redisTemplate != null) {
            try {
                String payload = objectMapper.writeValueAsString(normalized);
                redisTemplate.opsForValue().set(
                        UI_HISTORY_KEY,
                        payload,
                        Math.max(1L, sessionTtlMinutes),
                        TimeUnit.MINUTES
                );
                fallbackUiHistories = normalized;
                return;
            } catch (Exception e) {
                logger.warn("Redis 保存 UI 历史失败，降级内存模式 - error: {}", e.getMessage());
            }
        }
        fallbackUiHistories = normalized;
    }

    /**
     * 读取前端“近期对话”列表，优先 Redis，异常时降级到内存。
     */
    public List<Map<String, Object>> loadUiChatHistories() {
        if (redisTemplate != null) {
            try {
                String payload = redisTemplate.opsForValue().get(UI_HISTORY_KEY);
                if (payload == null || payload.isBlank()) {
                    return new ArrayList<>();
                }
                List<Map<String, Object>> loaded = objectMapper.readValue(
                        payload, new TypeReference<List<Map<String, Object>>>() {
                        });
                List<Map<String, Object>> normalized = normalizeUiHistories(loaded);
                fallbackUiHistories = normalized;
                return normalized;
            } catch (Exception e) {
                logger.warn("Redis 读取 UI 历史失败，降级内存模式 - error: {}", e.getMessage());
            }
        }
        return normalizeUiHistories(fallbackUiHistories);
    }

    private void persistSession(ChatSession session, String operation) {
        if (redisTemplate != null) {
            try {
                saveSessionToRedis(session);
                refreshUiHistoriesTtl();
                fallbackSessions.remove(session.getSessionId());
                return;
            } catch (Exception e) {
                logger.warn("Redis {}失败，降级内存模式 - SessionId: {}, error: {}",
                        operation, session.getSessionId(), e.getMessage());
            }
        }

        fallbackSessions.put(session.getSessionId(), session);
    }

    private void saveSessionToRedis(ChatSession session) throws JsonProcessingException {
        SessionSnapshot snapshot = toSessionSnapshot(session);
        String payload = objectMapper.writeValueAsString(snapshot);
        redisTemplate.opsForValue().set(
                buildSessionKey(session.getSessionId()),
                payload,
                Math.max(1L, sessionTtlMinutes),
                TimeUnit.MINUTES
        );
    }

    private Optional<ChatSession> loadSessionFromRedis(String sessionId) throws JsonProcessingException {
        String payload = redisTemplate.opsForValue().get(buildSessionKey(sessionId));
        if (payload == null || payload.isBlank()) {
            return Optional.empty();
        }

        SessionSnapshot snapshot = objectMapper.readValue(payload, SessionSnapshot.class);
        ChatSession session = toChatSession(snapshot, sessionId);
        return Optional.of(session);
    }

    private SessionSnapshot toSessionSnapshot(ChatSession session) {
        SessionSnapshot snapshot = new SessionSnapshot();
        snapshot.setSessionId(session.getSessionId());
        snapshot.setCreateTime(session.getCreateTime());

        List<SessionMessage> messages = new ArrayList<>();
        for (Map<String, String> item : session.getHistory()) {
            SessionMessage message = new SessionMessage();
            message.setRole(item.get("role"));
            message.setContent(item.get("content"));
            messages.add(message);
        }
        snapshot.setMessages(messages);
        return snapshot;
    }

    private ChatSession toChatSession(SessionSnapshot snapshot, String fallbackSessionId) {
        String resolvedSessionId = fallbackSessionId;
        long createTime = System.currentTimeMillis();

        if (snapshot != null) {
            if (snapshot.getSessionId() != null && !snapshot.getSessionId().isBlank()) {
                resolvedSessionId = snapshot.getSessionId();
            }
            if (snapshot.getCreateTime() > 0) {
                createTime = snapshot.getCreateTime();
            }
        }

        List<Map<String, String>> history = new ArrayList<>();
        if (snapshot != null && snapshot.getMessages() != null) {
            for (SessionMessage message : snapshot.getMessages()) {
                if (message == null) {
                    continue;
                }
                Map<String, String> item = new HashMap<>();
                item.put("role", message.getRole());
                item.put("content", message.getContent());
                history.add(item);
            }
        }

        ChatSession session = new ChatSession(resolvedSessionId, createTime, history);
        return session;
    }

    private SessionInfoResponse buildSessionInfoResponse(ChatSession session) {
        SessionInfoResponse response = new SessionInfoResponse();
        response.setSessionId(session.getSessionId());
        response.setMessagePairCount(session.getMessagePairCount());
        response.setCreateTime(session.getCreateTime());
        return response;
    }

    private String buildSessionKey(String sessionId) {
        return SESSION_KEY_PREFIX + sessionId;
    }

    private List<Map<String, Object>> normalizeUiHistories(List<Map<String, Object>> histories) {
        if (histories == null || histories.isEmpty()) {
            return new ArrayList<>();
        }

        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Map<String, Object> item : histories) {
            if (item == null) {
                continue;
            }
            String id = safeToString(item.get("id"));
            if (id.isBlank()) {
                continue;
            }

            Map<String, Object> copied = new HashMap<>();
            copied.put("id", id);
            copied.put("title", safeToString(item.get("title")));
            copied.put("createdAt", safeToString(item.get("createdAt")));
            copied.put("updatedAt", safeToString(item.get("updatedAt")));
            normalized.add(copied);
        }
        return normalized;
    }

    private void refreshUiHistoriesTtl() {
        if (redisTemplate == null) {
            return;
        }

        try {
            Boolean exists = redisTemplate.hasKey(UI_HISTORY_KEY);
            if (Boolean.TRUE.equals(exists)) {
                redisTemplate.expire(UI_HISTORY_KEY, Math.max(1L, sessionTtlMinutes), TimeUnit.MINUTES);
            }
        } catch (Exception e) {
            logger.warn("刷新 UI 历史 TTL 失败 - error: {}", e.getMessage());
        }
    }

    /**
     * 清理 UI 历史索引中的指定会话，避免会话被清空后左侧仍显示无效条目。
     */
    private void removeUiHistory(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }

        List<Map<String, Object>> currentHistories = loadUiChatHistories();
        if (currentHistories.isEmpty()) {
            return;
        }

        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> item : currentHistories) {
            if (item == null) {
                continue;
            }
            String id = safeToString(item.get("id"));
            if (!sessionId.equals(id)) {
                filtered.add(item);
            }
        }

        if (filtered.size() != currentHistories.size()) {
            saveUiChatHistories(filtered);
        }
    }

    private String safeToString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
