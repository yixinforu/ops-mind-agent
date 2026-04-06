package org.example.config;

import io.milvus.client.MilvusServiceClient;
import org.example.client.MilvusClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PreDestroy;

/**
 * Milvus 配置类
 * 负责创建和管理 MilvusServiceClient Bean
 */
@Configuration
public class MilvusConfig {

    private static final Logger logger = LoggerFactory.getLogger(MilvusConfig.class);

    @Autowired
    private MilvusClientFactory milvusClientFactory;

    private MilvusServiceClient milvusClient;

    /**
     * 创建 MilvusServiceClient Bean
     * 
     * @return MilvusServiceClient 实例
     */
    @Bean
    public MilvusServiceClient milvusServiceClient() {
        logger.info("正在初始化 Milvus 客户端...");
        milvusClient = milvusClientFactory.createClient();
        logger.info("Milvus 客户端初始化完成");
        return milvusClient;
    }

    /**
     * 应用关闭时清理资源
     */
    @PreDestroy
    public void cleanup() {
        if (milvusClient != null) {
            logger.info("正在关闭 Milvus 客户端连接...");
            milvusClient.close();
            logger.info("Milvus 客户端连接已关闭");
        }
    }
}
