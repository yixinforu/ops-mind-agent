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

            int maxMessages = maxWindowSize * 2;
            while (messageHistory.size() > maxMessages) {
                messageHistory.remove(0);
                if (!messageHistory.isEmpty()) {
                    messageHistory.remove(0);
                }
            }
        } finally {
            lock.unlock();
        }
    }

    public List<Map<String, String>> getHistory() {
        lock.lock();
        try {
            return new ArrayList<>(messageHistory);
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
