package com.ops.service.session;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ops.dto.chat.SessionInfoResponse;
import com.ops.entity.ChatMessageEntity;
import com.ops.entity.ChatSessionEntity;
import com.ops.repository.ChatMessageRepository;
import com.ops.repository.ChatSessionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 会话管理服务（MySQL 持久化 + Redis 上下文缓存）
 */
@Service
public class ChatSessionService {

    private static final Logger logger = LoggerFactory.getLogger(ChatSessionService.class);
    private static final String DEFAULT_SESSION_TITLE = "新对话";
    private static final String AI_OPS_TASK_PROMPT = "[系统任务] 执行 AI Ops 自动告警分析";
    private static final String CONTEXT_CACHE_KEY_PREFIX = "chat:ctx:";
    private static final int MAX_UI_HISTORY_SIZE = 50;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired(required = false)
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${chat.session.max-window-size:6}")
    private int maxWindowSize;

    @Value("${chat.session.context-cache.ttl-minutes:60}")
    private long contextCacheTtlMinutes;

    /**
     * 获取或创建会话元数据
     */
    @Transactional
    public ChatSession getOrCreateSession(Long userId, String sessionId) {
        Long safeUserId = normalizeUserId(userId);
        String effectiveSessionId = (sessionId == null || sessionId.isBlank())
                ? UUID.randomUUID().toString()
                : sessionId;

        ChatSessionEntity entity = findOrCreateSession(safeUserId, effectiveSessionId);
        return toChatSession(entity);
    }

    /**
     * 读取最近窗口历史（给大模型）
     */
    public List<Map<String, String>> getHistory(Long userId, ChatSession session) {
        if (session == null || session.getSessionId() == null || session.getSessionId().isBlank()) {
            return new ArrayList<>();
        }

        Long safeUserId = normalizeUserId(userId != null ? userId : session.getUserId());
        String sessionId = session.getSessionId();

        Optional<List<Map<String, String>>> cachedHistory = loadContextHistoryFromCache(safeUserId, sessionId);
        if (cachedHistory.isPresent()) {
            return cachedHistory.get();
        }

        Optional<ChatSessionEntity> sessionOptional = chatSessionRepository
                .findByUserIdAndSessionIdAndDeletedFalse(safeUserId, sessionId);
        if (sessionOptional.isEmpty()) {
            return new ArrayList<>();
        }

        int limit = Math.max(1, maxWindowSize) * 2;
        List<ChatMessageEntity> recentMessages = chatMessageRepository
                .findByUserIdAndSessionIdAndDeletedFalseOrderBySeqNoDesc(
                        safeUserId,
                        sessionId,
                        PageRequest.of(0, limit)
                );

        List<Map<String, String>> history = toRoleContentMapList(recentMessages);
        Collections.reverse(history);
        saveContextHistoryToCache(safeUserId, sessionId, history);
        return history;
    }

    /**
     * 兼容旧调用方式：从会话对象读取 userId
     */
    public List<Map<String, String>> getHistory(ChatSession session) {
        return getHistory(session == null ? null : session.getUserId(), session);
    }

    /**
     * 获取会话完整消息历史（用于前端展示）
     */
    public Optional<List<Map<String, String>>> getSessionMessages(Long userId, String sessionId) {
        Long safeUserId = normalizeUserId(userId);
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }

        Optional<ChatSessionEntity> sessionOptional = chatSessionRepository
                .findByUserIdAndSessionIdAndDeletedFalse(safeUserId, sessionId);
        if (sessionOptional.isEmpty()) {
            return Optional.empty();
        }

        List<ChatMessageEntity> messages = chatMessageRepository
                .findByUserIdAndSessionIdAndDeletedFalseOrderBySeqNoAsc(safeUserId, sessionId);
        return Optional.of(toRoleContentMapList(messages));
    }

    @Transactional
    public void addMessage(Long userId, ChatSession session, String userQuestion, String aiAnswer) {
        Long safeUserId = normalizeUserId(userId);
        String sessionId = resolveSessionId(session);

        appendMessagePair(
                safeUserId,
                sessionId,
                safeString(userQuestion),
                safeString(aiAnswer),
                true
        );

        if (session != null) {
            session.addMessage(safeString(userQuestion), safeString(aiAnswer), maxWindowSize);
        }
        refreshContextCacheFromDb(safeUserId, sessionId);
    }

    /**
     * 将 AI Ops 自动分析摘要写入会话历史，便于后续 chat/chat_stream 继续追问时继承上下文。
     */
    @Transactional
    public void addAiOpsSummary(Long userId, ChatSession session, String summary) {
        Long safeUserId = normalizeUserId(userId);
        String sessionId = resolveSessionId(session);

        appendMessagePair(
                safeUserId,
                sessionId,
                AI_OPS_TASK_PROMPT,
                safeString(summary),
                false
        );

        if (session != null) {
            session.addMessage(AI_OPS_TASK_PROMPT, safeString(summary), maxWindowSize);
        }
        refreshContextCacheFromDb(safeUserId, sessionId);
        logger.info("会话 {} 已写入 AI Ops 摘要上下文", sessionId);
    }

    /**
     * 将 AI Ops 完整报告写入会话历史，确保重新打开会话时能看到完整内容。
     */
    @Transactional
    public void addAiOpsReport(Long userId, ChatSession session, String fullReport) {
        Long safeUserId = normalizeUserId(userId);
        String sessionId = resolveSessionId(session);

        appendMessagePair(
                safeUserId,
                sessionId,
                AI_OPS_TASK_PROMPT,
                safeString(fullReport),
                false
        );

        if (session != null) {
            session.addMessage(AI_OPS_TASK_PROMPT, safeString(fullReport), maxWindowSize);
        }
        refreshContextCacheFromDb(safeUserId, sessionId);
        logger.info("会话 {} 已写入 AI Ops 完整报告", sessionId);
    }

    /**
     * 软删除会话及对应消息
     */
    @Transactional
    public boolean clearHistory(Long userId, String sessionId) {
        Long safeUserId = normalizeUserId(userId);
        if (sessionId == null || sessionId.isBlank()) {
            return false;
        }

        Optional<ChatSessionEntity> sessionOptional = chatSessionRepository
                .findByUserIdAndSessionIdAndDeletedFalse(safeUserId, sessionId);
        if (sessionOptional.isEmpty()) {
            return false;
        }

        ChatSessionEntity entity = sessionOptional.get();
        entity.setDeleted(true);
        entity.setMessageCount(0);
        entity.setLastMessageAt(null);
        entity.setUpdatedAt(LocalDateTime.now());
        chatSessionRepository.save(entity);

        chatMessageRepository.softDeleteByUserIdAndSessionId(safeUserId, sessionId);
        evictContextCache(safeUserId, sessionId);
        logger.info("会话历史已清空 - userId: {}, sessionId: {}", safeUserId, sessionId);
        return true;
    }

    public Optional<SessionInfoResponse> getSessionInfo(Long userId, String sessionId) {
        Long safeUserId = normalizeUserId(userId);
        if (sessionId == null || sessionId.isBlank()) {
            return Optional.empty();
        }

        Optional<ChatSessionEntity> sessionOptional = chatSessionRepository
                .findByUserIdAndSessionIdAndDeletedFalse(safeUserId, sessionId);
        if (sessionOptional.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(buildSessionInfoResponse(sessionOptional.get()));
    }

    /**
     * 保存前端“近期对话”列表到 MySQL。
     * 一期改造中仅做标题同步，真正列表以 chat_session 为准。
     */
    @Transactional
    public void saveUiChatHistories(Long userId, List<Map<String, Object>> histories) {
        Long safeUserId = normalizeUserId(userId);
        List<Map<String, Object>> normalized = normalizeUiHistories(histories);
        if (normalized.isEmpty()) {
            return;
        }

        Map<String, String> titleBySessionId = new LinkedHashMap<>();
        for (Map<String, Object> item : normalized) {
            String id = safeToString(item.get("id"));
            if (id.isBlank()) {
                continue;
            }
            String title = safeToString(item.get("title"));
            titleBySessionId.put(id, title.isBlank() ? DEFAULT_SESSION_TITLE : limitTitle(title));
        }

        if (titleBySessionId.isEmpty()) {
            return;
        }

        List<ChatSessionEntity> sessionEntities = chatSessionRepository
                .findByUserIdAndSessionIdInAndDeletedFalse(safeUserId, titleBySessionId.keySet());

        boolean changed = false;
        for (ChatSessionEntity sessionEntity : sessionEntities) {
            String title = titleBySessionId.get(sessionEntity.getSessionId());
            if (title != null && !title.equals(sessionEntity.getTitle())) {
                sessionEntity.setTitle(title);
                changed = true;
            }
        }

        if (changed) {
            chatSessionRepository.saveAll(sessionEntities);
        }
    }

    /**
     * 读取前端“近期对话”列表（以 MySQL 会话元数据为准）
     */
    public List<Map<String, Object>> loadUiChatHistories(Long userId) {
        Long safeUserId = normalizeUserId(userId);
        List<ChatSessionEntity> sessions = chatSessionRepository
                .findByUserIdAndDeletedFalseOrderByUpdatedAtDesc(
                        safeUserId,
                        PageRequest.of(0, MAX_UI_HISTORY_SIZE)
                );

        List<Map<String, Object>> histories = new ArrayList<>();
        for (ChatSessionEntity session : sessions) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", session.getSessionId());
            item.put("title", session.getTitle() == null || session.getTitle().isBlank()
                    ? DEFAULT_SESSION_TITLE
                    : session.getTitle());
            item.put("createdAt", toIsoString(session.getCreatedAt()));
            item.put("updatedAt", toIsoString(session.getUpdatedAt()));
            histories.add(item);
        }
        return histories;
    }

    @Transactional
    protected void appendMessagePair(
            Long userId,
            String sessionId,
            String userContent,
            String assistantContent,
            boolean useUserContentAsTitle
    ) {
        ChatSessionEntity sessionEntity = lockOrCreateSession(userId, sessionId);

        int maxSeq = Optional.ofNullable(chatMessageRepository.findMaxSeqNo(userId, sessionId)).orElse(0);
        LocalDateTime now = LocalDateTime.now();

        ChatMessageEntity userMessage = new ChatMessageEntity();
        userMessage.setUserId(userId);
        userMessage.setSessionId(sessionId);
        userMessage.setSeqNo(maxSeq + 1);
        userMessage.setRole("user");
        userMessage.setContent(userContent);
        userMessage.setCreatedAt(now);
        userMessage.setDeleted(false);

        ChatMessageEntity assistantMessage = new ChatMessageEntity();
        assistantMessage.setUserId(userId);
        assistantMessage.setSessionId(sessionId);
        assistantMessage.setSeqNo(maxSeq + 2);
        assistantMessage.setRole("assistant");
        assistantMessage.setContent(assistantContent);
        assistantMessage.setCreatedAt(now);
        assistantMessage.setDeleted(false);

        chatMessageRepository.save(userMessage);
        chatMessageRepository.save(assistantMessage);

        int currentCount = sessionEntity.getMessageCount() == null ? 0 : sessionEntity.getMessageCount();
        sessionEntity.setMessageCount(currentCount + 2);
        sessionEntity.setDeleted(false);
        sessionEntity.setLastMessageAt(now);
        sessionEntity.setUpdatedAt(now);

        if (useUserContentAsTitle && shouldUpdateTitle(sessionEntity)) {
            sessionEntity.setTitle(buildSessionTitle(userContent));
        }

        chatSessionRepository.save(sessionEntity);
    }

    private ChatSessionEntity findOrCreateSession(Long userId, String sessionId) {
        Optional<ChatSessionEntity> existingOptional = chatSessionRepository.findByUserIdAndSessionId(userId, sessionId);
        if (existingOptional.isPresent()) {
            ChatSessionEntity existing = existingOptional.get();
            if (Boolean.TRUE.equals(existing.getDeleted())) {
                existing.setDeleted(false);
                existing.setMessageCount(0);
                existing.setLastMessageAt(null);
                existing.setTitle(DEFAULT_SESSION_TITLE);
                existing.setUpdatedAt(LocalDateTime.now());
                return chatSessionRepository.save(existing);
            }
            return existing;
        }

        ChatSessionEntity created = new ChatSessionEntity();
        created.setUserId(userId);
        created.setSessionId(sessionId);
        created.setTitle(DEFAULT_SESSION_TITLE);
        created.setMessageCount(0);
        created.setDeleted(false);
        try {
            return chatSessionRepository.save(created);
        } catch (DataIntegrityViolationException e) {
            // 并发创建同一 session_id 时回查已存在记录
            return chatSessionRepository.findByUserIdAndSessionId(userId, sessionId)
                    .orElseThrow(() -> e);
        }
    }

    /**
     * 写消息前对会话行加锁，确保 seq_no 连续。
     */
    private ChatSessionEntity lockOrCreateSession(Long userId, String sessionId) {
        Optional<ChatSessionEntity> lockedOptional = chatSessionRepository.findForUpdate(userId, sessionId);
        if (lockedOptional.isPresent()) {
            ChatSessionEntity sessionEntity = lockedOptional.get();
            if (Boolean.TRUE.equals(sessionEntity.getDeleted())) {
                sessionEntity.setDeleted(false);
                sessionEntity.setMessageCount(0);
                sessionEntity.setLastMessageAt(null);
                sessionEntity.setTitle(DEFAULT_SESSION_TITLE);
                sessionEntity.setUpdatedAt(LocalDateTime.now());
            }
            return sessionEntity;
        }

        ChatSessionEntity created = new ChatSessionEntity();
        created.setUserId(userId);
        created.setSessionId(sessionId);
        created.setTitle(DEFAULT_SESSION_TITLE);
        created.setMessageCount(0);
        created.setDeleted(false);
        try {
            return chatSessionRepository.save(created);
        } catch (DataIntegrityViolationException e) {
            return chatSessionRepository.findForUpdate(userId, sessionId)
                    .orElseThrow(() -> e);
        }
    }

    private SessionInfoResponse buildSessionInfoResponse(ChatSessionEntity sessionEntity) {
        SessionInfoResponse response = new SessionInfoResponse();
        response.setSessionId(sessionEntity.getSessionId());
        int messageCount = sessionEntity.getMessageCount() == null ? 0 : sessionEntity.getMessageCount();
        response.setMessagePairCount(Math.max(0, messageCount) / 2);
        response.setCreateTime(toEpochMillis(sessionEntity.getCreatedAt()));
        return response;
    }

    private ChatSession toChatSession(ChatSessionEntity sessionEntity) {
        return new ChatSession(
                sessionEntity.getSessionId(),
                sessionEntity.getUserId(),
                toEpochMillis(sessionEntity.getCreatedAt()),
                new ArrayList<>()
        );
    }

    private List<Map<String, String>> toRoleContentMapList(List<ChatMessageEntity> messages) {
        List<Map<String, String>> result = new ArrayList<>();
        if (messages == null || messages.isEmpty()) {
            return result;
        }

        for (ChatMessageEntity message : messages) {
            if (message == null) {
                continue;
            }
            Map<String, String> item = new HashMap<>();
            item.put("role", safeString(message.getRole()));
            item.put("content", safeString(message.getContent()));
            result.add(item);
        }
        return result;
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

    private boolean shouldUpdateTitle(ChatSessionEntity sessionEntity) {
        String title = sessionEntity.getTitle();
        return title == null || title.isBlank() || DEFAULT_SESSION_TITLE.equals(title);
    }

    private String buildSessionTitle(String userMessage) {
        String content = safeString(userMessage).trim();
        if (content.isEmpty()) {
            return DEFAULT_SESSION_TITLE;
        }
        if (content.length() <= 30) {
            return content;
        }
        return content.substring(0, 30) + "...";
    }

    private String limitTitle(String title) {
        String content = safeString(title).trim();
        if (content.isEmpty()) {
            return DEFAULT_SESSION_TITLE;
        }
        if (content.length() <= 255) {
            return content;
        }
        return content.substring(0, 255);
    }

    private String resolveSessionId(ChatSession session) {
        if (session != null && session.getSessionId() != null && !session.getSessionId().isBlank()) {
            return session.getSessionId();
        }
        return UUID.randomUUID().toString();
    }

    private Long normalizeUserId(Long userId) {
        return userId == null ? 0L : userId;
    }

    private String safeString(String value) {
        return value == null ? "" : value;
    }

    private String safeToString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private long toEpochMillis(LocalDateTime time) {
        if (time == null) {
            return System.currentTimeMillis();
        }
        return time.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private String toIsoString(LocalDateTime time) {
        return time == null ? LocalDateTime.now().toString() : time.toString();
    }

    private Optional<List<Map<String, String>>> loadContextHistoryFromCache(Long userId, String sessionId) {
        if (redisTemplate == null) {
            return Optional.empty();
        }

        String key = buildContextCacheKey(userId, sessionId);
        try {
            String payload = redisTemplate.opsForValue().get(key);
            if (payload == null || payload.isBlank()) {
                return Optional.empty();
            }

            List<Map<String, String>> cached = objectMapper.readValue(
                    payload,
                    new TypeReference<List<Map<String, String>>>() {
                    }
            );
            return Optional.of(normalizeRoleContentList(cached));
        } catch (Exception e) {
            logger.warn("读取上下文缓存失败，降级 MySQL - userId: {}, sessionId: {}, error: {}",
                    userId, sessionId, e.getMessage());
            return Optional.empty();
        }
    }

    private void saveContextHistoryToCache(Long userId, String sessionId, List<Map<String, String>> history) {
        if (redisTemplate == null) {
            return;
        }

        try {
            List<Map<String, String>> normalized = normalizeRoleContentList(history);
            String payload = objectMapper.writeValueAsString(normalized);
            redisTemplate.opsForValue().set(
                    buildContextCacheKey(userId, sessionId),
                    payload,
                    Math.max(1L, contextCacheTtlMinutes),
                    TimeUnit.MINUTES
            );
        } catch (Exception e) {
            logger.warn("写入上下文缓存失败 - userId: {}, sessionId: {}, error: {}",
                    userId, sessionId, e.getMessage());
        }
    }

    private void refreshContextCacheFromDb(Long userId, String sessionId) {
        if (redisTemplate == null) {
            return;
        }

        try {
            int limit = Math.max(1, maxWindowSize) * 2;
            List<ChatMessageEntity> recentMessages = chatMessageRepository
                    .findByUserIdAndSessionIdAndDeletedFalseOrderBySeqNoDesc(
                            userId,
                            sessionId,
                            PageRequest.of(0, limit)
                    );

            List<Map<String, String>> history = toRoleContentMapList(recentMessages);
            Collections.reverse(history);
            saveContextHistoryToCache(userId, sessionId, history);
        } catch (Exception e) {
            logger.warn("刷新上下文缓存失败 - userId: {}, sessionId: {}, error: {}",
                    userId, sessionId, e.getMessage());
        }
    }

    private void evictContextCache(Long userId, String sessionId) {
        if (redisTemplate == null) {
            return;
        }

        try {
            redisTemplate.delete(buildContextCacheKey(userId, sessionId));
        } catch (Exception e) {
            logger.warn("删除上下文缓存失败 - userId: {}, sessionId: {}, error: {}",
                    userId, sessionId, e.getMessage());
        }
    }

    private String buildContextCacheKey(Long userId, String sessionId) {
        return CONTEXT_CACHE_KEY_PREFIX + userId + ":" + sessionId;
    }

    private List<Map<String, String>> normalizeRoleContentList(List<Map<String, String>> history) {
        if (history == null || history.isEmpty()) {
            return new ArrayList<>();
        }

        List<Map<String, String>> normalized = new ArrayList<>();
        for (Map<String, String> message : history) {
            if (message == null) {
                continue;
            }
            String role = safeString(message.get("role"));
            String content = safeString(message.get("content"));
            if (role.isBlank()) {
                continue;
            }

            Map<String, String> copied = new HashMap<>();
            copied.put("role", role);
            copied.put("content", content);
            normalized.add(copied);
        }
        return normalized;
    }
}
