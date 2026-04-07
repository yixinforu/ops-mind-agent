package org.example.service.session;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 会话信息
 */
public class ChatSession {
    private final String sessionId;
    private final List<Map<String, String>> messageHistory;
    private final long createTime;
    private final ReentrantLock lock;

    public ChatSession(String sessionId) {
        this.sessionId = sessionId;
        this.messageHistory = new ArrayList<>();
        this.createTime = System.currentTimeMillis();
        this.lock = new ReentrantLock();
    }

    /**
     * 基于已有会话快照恢复会话对象
     */
    public ChatSession(String sessionId, long createTime, List<Map<String, String>> messageHistory) {
        this.sessionId = sessionId;
        this.createTime = createTime > 0 ? createTime : System.currentTimeMillis();
        this.messageHistory = new ArrayList<>();
        if (messageHistory != null) {
            for (Map<String, String> msg : messageHistory) {
                if (msg == null) {
                    continue;
                }
                Map<String, String> copied = new HashMap<>();
                copied.put("role", msg.get("role"));
                copied.put("content", msg.get("content"));
                this.messageHistory.add(copied);
            }
        }
        this.lock = new ReentrantLock();
    }

    public String getSessionId() {
        return sessionId;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void addMessage(String userQuestion, String aiAnswer, int maxWindowSize) {
        lock.lock();
        try {
            Map<String, String> userMsg = new HashMap<>();
            userMsg.put("role", "user");
            userMsg.put("content", userQuestion);
            messageHistory.add(userMsg);

            Map<String, String> assistantMsg = new HashMap<>();
            assistantMsg.put("role", "assistant");
            assistantMsg.put("content", aiAnswer);
            messageHistory.add(assistantMsg);
        } finally {
            lock.unlock();
        }
    }

    public List<Map<String, String>> getHistory() {
        lock.lock();
        try {
            List<Map<String, String>> copiedHistory = new ArrayList<>();
            for (Map<String, String> msg : messageHistory) {
                Map<String, String> copied = new HashMap<>();
                copied.put("role", msg.get("role"));
                copied.put("content", msg.get("content"));
                copiedHistory.add(copied);
            }
            return copiedHistory;
        } finally {
            lock.unlock();
        }
    }

    /**
     * 获取最近窗口的历史消息（按消息对裁剪）。
     */
    public List<Map<String, String>> getRecentHistory(int maxWindowSize) {
        lock.lock();
        try {
            List<Map<String, String>> fullHistory = getHistory();
            int maxMessages = Math.max(1, maxWindowSize) * 2;
            if (fullHistory.size() <= maxMessages) {
                return fullHistory;
            }
            return new ArrayList<>(fullHistory.subList(fullHistory.size() - maxMessages, fullHistory.size()));
        } finally {
            lock.unlock();
        }
    }

    public void clearHistory() {
        lock.lock();
        try {
            messageHistory.clear();
        } finally {
            lock.unlock();
        }
    }

    public int getMessagePairCount() {
        lock.lock();
        try {
            return messageHistory.size() / 2;
        } finally {
            lock.unlock();
        }
    }
}
