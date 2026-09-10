package com.sinosig.sluw.application.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.sinosig.sluw.application.config.PromptTemplateConfig;
import com.sinosig.sluw.application.dto.AgentState;
import com.sinosig.sluw.application.dto.IntentType;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 工具执行节点
 * <p>
 * 职责：利用 Spring AI 内置 Function Calling 机制，一次性完成“工具选择 → 工具执行 → 结果总结”。
 * 生成的自然语言回答将存入 context，供后续回答生成节点直接使用。
 * </p>
 *
 * @author SinoSig AI Team
 */
@Component
public class ToolExecutorNode extends BaseNode {

    private static final Logger logger = LoggerFactory.getLogger(ToolExecutorNode.class);

    private final ChatClient toolEnabledChatClient;
    private final PromptTemplateConfig templateConfig;

    public ToolExecutorNode(@Qualifier("toolEnabledChatClient") ChatClient toolEnabledChatClient,
                            PromptTemplateConfig templateConfig) {
        this.toolEnabledChatClient = toolEnabledChatClient;
        this.templateConfig = templateConfig;
    }

    @Override
    protected Map<String, Object> doProcess(OverAllState state) {
        logger.debug("=== 工具执行节点开始 ===");
        AgentState agentState = extractAgentState(state);

        executeToolLogic(agentState);

        logger.debug("=== 工具执行节点完成 ===");
        return buildUpdateMap(agentState);
    }

    private void executeToolLogic(AgentState agentState) {
        String userInput = agentState.getUserInput();
        IntentType intentType = agentState.getIntentType();

        logger.info("开始执行工具逻辑，用户输入：{}，意图类型：{}", userInput, intentType);

        // 构建包含历史对话的提示词
        String historyText = "";
        if (agentState.isUseRefinerMemory()) {
            historyText = templateConfig.formatHistory(agentState.getRecentHistory(2));
        }
        String prompt = templateConfig.buildToolExecutorPrompt(userInput, intentType.name(), historyText);

        try {
            ChatResponse chatResponse = toolEnabledChatClient.prompt()
                    .user(prompt)
                    .call()
                    .chatResponse();

            if (chatResponse == null || chatResponse.getResults() == null || chatResponse.getResults().isEmpty()) {
                throw new RuntimeException("模型返回null或无效响应");
            }

            // 获取最终的自然语言回答
            String finalResponse = chatResponse.getResult().getOutput().getText();

            if (StringUtils.isBlank(finalResponse)) {
                throw new RuntimeException("模型未返回有效内容");
            }

            // 累积 Token 用量
            Usage usage = chatResponse.getMetadata() != null ? chatResponse.getMetadata().getUsage() : null;
            if (usage != null) {
                agentState.accumulateTokenUsage(usage);
                logger.debug("工具执行 Token 用量 - 输入: {}, 输出: {}, 总计: {}",
                        usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
            }

            agentState.getContext().put("tool_execution_result", finalResponse);
            logger.info("工具执行与总结完成，回复长度：{}", finalResponse.length());

        } catch (Exception e) {
            logger.error("工具执行失败：{}", e.getMessage(), e);
            throw new RuntimeException("工具执行失败，请稍后重试", e);
        }
    }

    private Map<String, Object> buildUpdateMap(AgentState agentState) {
        Map<String, Object> updateMap = new HashMap<>();
        updateMap.put("agent_state", agentState);
        return updateMap;
    }
}