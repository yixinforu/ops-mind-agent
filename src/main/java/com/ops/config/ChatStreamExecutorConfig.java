package com.ops.config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.ThreadPoolExecutor;

/**
 * 流式接口线程池配置。
 * 为长连接 SSE 请求提供有界线程池，控制并发与排队上限。
 */
@Configuration
public class ChatStreamExecutorConfig {

    @Autowired
    private ChatStreamExecutorProperties properties;

    /**
     * 为聊天流式输出与 AI Ops 流式分析提供统一的执行线程池。
     */
    @Bean("chatStreamTaskExecutor")
    public ThreadPoolTaskExecutor chatStreamTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getCoreSize());
        executor.setMaxPoolSize(properties.getMaxSize());
        executor.setQueueCapacity(properties.getQueueCapacity());
        executor.setKeepAliveSeconds(properties.getKeepAliveSeconds());
        executor.setAllowCoreThreadTimeOut(true);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(properties.getAwaitTerminationSeconds());
        executor.setThreadNamePrefix(properties.getThreadNamePrefix());
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.initialize();
        return executor;
    }
}
