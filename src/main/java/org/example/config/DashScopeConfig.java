package org.example.config;

import okhttp3.OkHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.OkHttp3ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * DashScope API 配置
 * 用于配置超时时间等参数
 */
@Configuration
public class DashScopeConfig {

    @Value("${spring.ai.dashscope.chat.options.timeout:180000}")
    private long timeout;

    /**
     * 配置 RestClient.Builder，设置超时时间
     * Spring AI 会自动使用这个 Bean
     */
    @Bean
    public RestClient.Builder restClientBuilder() {
        // 创建自定义的 OkHttpClient，设置超时时间
        OkHttpClient okHttpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofMillis(timeout))
                .readTimeout(Duration.ofMillis(timeout))
                .writeTimeout(Duration.ofMillis(timeout))
                .callTimeout(Duration.ofMillis(timeout))
                .build();

        // 创建 RestClient.Builder 并配置 OkHttpClient
        return RestClient.builder()
                .requestFactory(new OkHttp3ClientHttpRequestFactory(okHttpClient));
    }
}
