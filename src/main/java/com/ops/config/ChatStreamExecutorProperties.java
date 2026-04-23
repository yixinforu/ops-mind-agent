package com.ops.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 流式对话线程池配置。
 * 用于限制 /api/chat_stream 与 /api/ai_ops 的并发执行资源，
 * 避免高峰期使用无界线程池导致线程数量失控。
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "chat.stream.executor")
public class ChatStreamExecutorProperties {

    /**
     * 核心线程数。
     */
    private int coreSize = 4;

    /**
     * 最大线程数。
     */
    private int maxSize = 16;

    /**
     * 等待队列容量。
     */
    private int queueCapacity = 100;

    /**
     * 非核心线程空闲保活时间，单位：秒。
     */
    private int keepAliveSeconds = 60;

    /**
     * 应用关闭时等待线程池收敛的时间，单位：秒。
     */
    private int awaitTerminationSeconds = 30;

    /**
     * 线程名前缀，便于排查流式请求相关日志。
     */
    private String threadNamePrefix = "chat-stream-";
}
