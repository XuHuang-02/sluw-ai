package com.sinosig.sluw.application.config;

import com.sinosig.sluw.application.tools.DbToolService;
import com.sinosig.sluw.application.tools.HttpToolService;
import com.sinosig.sluw.application.tools.McpToolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 智能体工具配置类
 *
 * @author SinoSig AI Team
 */
@Configuration
public class AgentToolConfig {

    private static final Logger logger = LoggerFactory.getLogger(AgentToolConfig.class);

    /**
     * 创建工具回调提供者，扫描 @Tool 注解方法。
     *
     * @param dbToolService    数据库工具服务
     * @param httpToolService  HTTP 工具服务
     * @param mcpToolService   MCP 工具服务
     * @return MethodToolCallbackProvider 实例
     */
    @Bean
    public MethodToolCallbackProvider methodToolCallbackProvider(
            DbToolService dbToolService,
            HttpToolService httpToolService,
            McpToolService mcpToolService) {

        logger.info("开始注册业务工具服务");
        return MethodToolCallbackProvider.builder()
                .toolObjects(dbToolService, httpToolService, mcpToolService)
                .build();
    }

    /**
     * 创建支持工具调用的 ChatClient。
     *
     * @param chatClientBuilder   ChatClient 构建器
     * @param toolCallbackProvider 工具回调提供者
     * @return 配置好的 ChatClient 实例
     */
    @Bean
    public ChatClient toolEnabledChatClient(ChatClient.Builder chatClientBuilder,
                                            MethodToolCallbackProvider toolCallbackProvider) {
        ToolCallback[] toolCallbacks = toolCallbackProvider.getToolCallbacks();
        logger.info("成功注册工具回调，数量：{}", toolCallbacks.length);

        return chatClientBuilder
                .defaultToolCallbacks(toolCallbacks)
                .build();
    }
}