package com.sinosig.sluw.application.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.sinosig.sluw.application.config.PromptTemplateConfig;
import com.sinosig.sluw.application.dto.AgentState;
import com.sinosig.sluw.application.dto.IntentType;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 统一回答生成节点（上下文聚合 + 最终回复生成）。
 * <p>在图执行阶段，仅做上下文聚合标记，不调用 LLM。</p>
 * <p>图执行结束后，由 AgentService 调用本类的 generateSync/generateStream 方法完成实际回复生成。</p>
 * <p>异常处理：同步生成时模型返回 null 直接抛出 RuntimeException；流式生成时返回错误提示 Flux。</p>
 *
 * @author SinoSig AI Team
 */
@Component
public class ResponseGeneratorNode extends BaseNode {

    private final ChatClient chatClient;
    private final PromptTemplateConfig templateConfig;

    public ResponseGeneratorNode(ChatClient chatClient, PromptTemplateConfig templateConfig) {
        this.chatClient = chatClient;
        this.templateConfig = templateConfig;
    }

    /**
     * 图执行阶段的入口：仅做上下文聚合，不生成回复。
     */
    @Override
    protected Map<String, Object> doProcess(OverAllState state) {
        logger.debug("=== 上下文聚合节点开始 ===");
        AgentState agentState = extractAgentState(state);

        if (agentState.getContext() == null) {
            agentState.setContext(new HashMap<>());
        }

        agentState.getContext().put("aggregated", true);
        logger.debug("上下文聚合完成，意图类型：{}", agentState.getIntentType());

        Map<String, Object> updateMap = new HashMap<>();
        updateMap.put("agent_state", agentState);
        return updateMap;
    }

    /**
     * 同步生成最终回复（在图执行后调用）。
     *
     * @param agentState 已聚合上下文的 AgentState
     * @return 最终回复字符串
     * @throws RuntimeException 当模型返回 null 时
     */
    public String generateSync(AgentState agentState) {
        String preGenerated = getPreGeneratedResponse(agentState);
        if (preGenerated != null) {
            logger.info("命中预生成回复（意图：{}），跳过 LLM 调用", agentState.getIntentType());
            agentState.setResponse(preGenerated);
            return preGenerated;
        }

        boolean useHistory = agentState.isUseRefinerMemory();
        String prompt = templateConfig.buildResponseGeneratorPrompt(agentState, useHistory);
        logger.debug("调用 LLM 生成最终回复，意图类型：{}", agentState.getIntentType());

        ChatResponse response;
        try {
            response = chatClient.prompt(prompt).call().chatResponse();
        } catch (Exception e) {
            logger.error("LLM 同步生成失败: {}", e.getMessage());
            throw new RuntimeException("模型繁忙，请主人猛戳左下角进行重试！", e);
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
     * 流式生成最终回复（在图执行后调用）。
     *
     * @param agentState 已聚合上下文的 AgentState
     * @return 流式内容 Flux，出错时返回错误提示
     */
    public Flux<String> generateStream(AgentState agentState) {
        String preGenerated = getPreGeneratedResponse(agentState);
        if (preGenerated != null) {
            logger.info("命中预生成回复（意图：{}），直接返回", agentState.getIntentType());
            agentState.setResponse(preGenerated);
            return Flux.just(preGenerated);
        }

        boolean useHistory = agentState.isUseRefinerMemory();
        String prompt = templateConfig.buildResponseGeneratorPrompt(agentState, useHistory);
        logger.debug("调用 LLM 流式生成最终回复，意图类型：{}", agentState.getIntentType());

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
                    return Flux.just("模型繁忙，请主人猛戳左下角进行重试！");
                });
    }

    private String getPreGeneratedResponse(AgentState agentState) {
        Map<String, Object> context = agentState.getContext();
        if (context == null) return null;

        if (agentState.getIntentType() == IntentType.TOOL_EXECUTION && context.containsKey("tool_execution_result")) {
            return (String) context.get("tool_execution_result");
        }

        if (agentState.getIntentType() == IntentType.CLARIFICATION && context.containsKey("clarification_response")) {
            return (String) context.get("clarification_response");
        }

        return null;
    }
}