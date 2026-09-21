package com.sinosig.sluw.application.node;

import com.sinosig.sluw.application.config.PromptTemplateConfig;
import com.sinosig.sluw.application.dto.AgentState;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.concurrent.atomic.AtomicReference;

/**
 * 风险概述生成器，支持同步和流式输出。
 * <p>由 AgentService 直接调用；每次请求只执行一次概述生成。</p>
 * <p>异常处理：同步生成时模型返回 null 直接抛出 RuntimeException；流式生成时返回错误提示 Flux。</p>
 *
 * @author SinoSig AI Team
 */
@Component
public class ResponseGeneratorNode {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ResponseGeneratorNode.class);
    private final ChatClient chatClient;
    private final PromptTemplateConfig templateConfig;

    public ResponseGeneratorNode(ChatClient chatClient, PromptTemplateConfig templateConfig) {
        this.chatClient = chatClient;
        this.templateConfig = templateConfig;
    }

    /**
     * 同步生成最终回复。
     *
     * @param agentState 当前会话资料
     * @return 最终回复字符串
     * @throws RuntimeException 当模型返回 null 时
     */
    public String generateSync(AgentState agentState) {
        boolean useHistory = agentState.isUseRefinerMemory();
        String prompt = templateConfig.buildResponseGeneratorPrompt(agentState, useHistory);
        logger.debug("调用 LLM 生成最终回复");

        ChatResponse response;
        try {
            response = chatClient.prompt(prompt).call().chatResponse();
        } catch (Exception e) {
            logger.error("LLM 同步生成失败: {}", e.getMessage());
            throw new RuntimeException("生成暂时失败，请稍后重试。", e);
        }

        if (response == null || response.getResult() == null) {
            throw new RuntimeException("模型返回null或无效响应");
        }

        String content = response.getResult().getOutput().getText();
        if (StringUtils.isBlank(content)) {
            throw new RuntimeException("模型返回内容为空");
        }

        Usage usage = response.getMetadata() != null ? response.getMetadata().getUsage() : null;
        if (usage != null) {
            agentState.accumulateTokenUsage(usage);
            logger.debug("同步生成 Token 用量 - 输入: {}, 输出: {}, 总计: {}",
                    usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
        }

        agentState.setResponse(content);
        logger.info("LLM 生成回复成功，长度：{}", content.length());
        return content;
    }

    /**
     * 流式生成最终回复。
     *
     * @param agentState 当前会话资料
     * @return 流式内容 Flux，出错时返回错误提示
     */
    public Flux<String> generateStream(AgentState agentState) {
        boolean useHistory = agentState.isUseRefinerMemory();
        String prompt = templateConfig.buildResponseGeneratorPrompt(agentState, useHistory);
        logger.debug("调用 LLM 流式生成最终回复");

        // 发起流式调用，获取 Flux<ChatResponse>
        Flux<ChatResponse> responseFlux = chatClient.prompt(prompt)
                .stream()
                .chatResponse();

        AtomicReference<Usage> usageRef = new AtomicReference<>();

        // 收集所有响应块，以便在流结束后获取 Usage，同时保证内容流正常输出
        return responseFlux
                .doOnNext(response -> {
                    if (response != null && response.getMetadata() != null) {
                        Usage usage = response.getMetadata().getUsage();
                        if (usage != null) {
                            usageRef.set(usage);
                        }
                    }
                })
                .map(response -> {
                    // 将 ChatResponse 转换为文本内容
                    if (response == null || response.getResult() == null) {
                        logger.info("最终回答流式原始块: null or empty result");
                        return "";
                    }
                    return response.getResult().getOutput().getText();
                })
                .filter(text -> !text.isEmpty()) // 过滤空字符串
                .doOnComplete(() -> {
                    // 5. 流结束后，统一处理 Token 统计
                    Usage finalUsage = usageRef.get();
                    if (finalUsage != null) {
                        agentState.accumulateTokenUsage(finalUsage);
                        logger.debug("流式生成结束 - Token 用量: 输入={}, 输出={}, 总计={}",
                                finalUsage.getPromptTokens(), finalUsage.getCompletionTokens(), finalUsage.getTotalTokens());
                    } else {
                        logger.warn("流式响应未包含 Usage 元数据（可能是模型不支持或网络截断）");
                    }
                })
                .onErrorResume(e -> {
                    logger.error("流式生成错误: {}", e.getMessage(), e);
                    return Flux.just("生成暂时失败，请稍后重试。");
                });
    }

}
