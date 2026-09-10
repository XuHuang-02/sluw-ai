package com.sinosig.sluw.application.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.sinosig.sluw.application.config.PromptTemplateConfig;
import com.sinosig.sluw.application.dto.AgentState;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 问题润色节点。
 * <p>对用户输入进行改写，提升意图识别和检索准确性。支持根据开关决定是否引入历史上下文。</p>
 * <p>异常处理：润色失败时降级使用原始输入，不影响后续流程，仅记录警告日志。</p>
 *
 * @author SinoSig AI Team
 */
@Component
public class QuestionRefinerNode extends BaseNode {

    private final ChatClient chatClient;
    private final PromptTemplateConfig templateConfig;

    public QuestionRefinerNode(ChatClient chatClient, PromptTemplateConfig templateConfig) {
        this.chatClient = chatClient;
        this.templateConfig = templateConfig;
    }

    @Override
    protected Map<String, Object> doProcess(OverAllState state) {
        logger.debug("=== 问题润色节点开始 ===");
        AgentState agentState = extractAgentState(state);

//        if (!agentState.isUseRefiner()) {
//            logger.debug("润色功能未启用，跳过润色");
//            return buildUpdateMap(agentState);
//        }

        String originalInput = agentState.getUserInput();
        if (StringUtils.isBlank(originalInput)) {
            logger.warn("用户输入为空，跳过润色");
            return buildUpdateMap(agentState);
        }

        agentState.setOriginalInput(originalInput);

        String historyText = "";
        if (agentState.isUseRefinerMemory()) {
            historyText = templateConfig.formatHistory(agentState.getRecentHistory(3));
        }
//        if(StringUtils.isBlank(historyText)){
//            logger.warn("暂无历史对话，跳过润色");
//            return buildUpdateMap(agentState);
//        }
        String prompt = templateConfig.buildQueryRefinerPrompt(originalInput, historyText);
        logger.debug("调用 LLM 进行问题润色，原始输入长度: {}", originalInput.length());

        ChatResponse response = null;
        try {
            response = chatClient.prompt(prompt).call().chatResponse();
        } catch (Exception e) {
            logger.warn("问题润色 LLM 调用异常，将使用原始输入: {}", e.getMessage());
        }

        if (response != null && response.getResult() != null) {
            String refined = response.getResult().getOutput().getText();
            if (StringUtils.isNotBlank(refined)) {
                agentState.setUserInput(refined.trim());
                Usage usage = response.getMetadata() != null ? response.getMetadata().getUsage() : null;
                if (usage != null) {
                    agentState.accumulateTokenUsage(usage);
                    logger.debug("润色 Token 用量 - 输入: {}, 输出: {}, 总计: {}",
                            usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
                }
                logger.info("问题润色成功: 原始={}, 改写后={}", originalInput, refined);
            } else {
                logger.warn("问题润色返回空，保留原始输入: {}", originalInput);
            }
        } else {
            logger.warn("LLM 响应为空，保留原始输入");
        }

        return buildUpdateMap(agentState);
    }

    private Map<String, Object> buildUpdateMap(AgentState agentState) {
        Map<String, Object> updateMap = new HashMap<>();
        updateMap.put("agent_state", agentState);
        return updateMap;
    }
}