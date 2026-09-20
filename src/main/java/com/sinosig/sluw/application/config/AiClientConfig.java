package com.sinosig.sluw.application.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * AI客户端配置类
 * 提供RestTemplate、ChatModel、ChatClient等Bean定义
 *
 * @author
 * @version 1.0
 */
@Configuration
public class AiClientConfig {

    private static final Logger logger = LoggerFactory.getLogger(AiClientConfig.class);
    @Value("${application.config.ragFlow.base.connectTimeoutMillis:10000}")
    private int ragConnectTimeoutMillis=10000;
    @Value("${application.config.ragFlow.base.readTimeoutMillis:60000}")
    private int ragReadTimeoutMillis=60000;

    /**
     * 创建RestTemplate Bean
     * 用于HTTP请求
     *
     * @return RestTemplate实例
     */

    @Bean
    public RestTemplate restTemplate() {
        if(ragConnectTimeoutMillis<1||ragReadTimeoutMillis<1)
            throw new IllegalArgumentException("RAG HTTP timeouts must be positive");
        SimpleClientHttpRequestFactory factory=new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(ragConnectTimeoutMillis);
        factory.setReadTimeout(ragReadTimeoutMillis);
        return new RestTemplate(factory);
    }

    @Bean
    public RestClientCustomizer dashScopeTimeoutCustomizer(
            @Value("${spring.ai.dashscope.rest-client.timeout:60s}") Duration timeout) {

        return builder -> {
            // Spring 6.1 自带的工厂，支持超时，无 deprecation
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout((int) timeout.toMillis());
            factory.setReadTimeout((int) timeout.toMillis());
            builder.requestFactory(factory);
        };
    }

    /**
     * 创建普通 ChatClient Bean（无工具，无自动历史注入）。
     * 历史对话由业务层通过 PromptTemplateConfig 手动注入。
     */
    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        logger.info("初始化普通 ChatClient（无自动历史）");
        return ChatClient.builder(chatModel)
                .defaultAdvisors(new SimpleLoggerAdvisor())
                .build();
    }
}
