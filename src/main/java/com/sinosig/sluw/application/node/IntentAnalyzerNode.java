package com.sinosig.sluw.application.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sinosig.sluw.application.config.PromptTemplateConfig;
import com.sinosig.sluw.application.dto.AgentState;
import com.sinosig.sluw.application.dto.IntentResult;
import com.sinosig.sluw.application.dto.IntentType;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 意图分析节点。
 * <p>职责：识别用户意图和置信度，不生成最终回复。</p>
 * <p>异常处理：模型返回 null 或解析失败时，直接抛出 RuntimeException，
 * 由上层 AgentService 统一处理并返回友好提示。</p>
 *
 * @author SinoSig AI Team
 */
@Component
public class IntentAnalyzerNode extends BaseNode {

    private static final Pattern JSON_PATTERN = Pattern.compile("```(?:json)?\\s*(.*?)\\s*```", Pattern.DOTALL);

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final PromptTemplateConfig templateConfig;

    public IntentAnalyzerNode(ChatClient chatClient,
                              ObjectMapper objectMapper,
                              PromptTemplateConfig templateConfig) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
        this.templateConfig = templateConfig;
    }

    @Override
    protected Map<String, Object> doProcess(OverAllState state) {
        String traceId = UUID.randomUUID().toString();
        logger.debug("[{}] === 意图分析节点开始 ===", traceId);
        AgentState agentState = extractAgentState(state);

        String userInput = agentState.getUserInput();
        if (StringUtils.isBlank(userInput)) {
            logger.warn("[{}] 用户输入为空，设为 UNKNOWN", traceId);
            setUnknownIntent(agentState);
            return buildUpdateMap(agentState);
        }

        String historyText = "";
        if (agentState.isUseRefinerMemory()) {
            historyText = templateConfig.formatHistory(agentState.getRecentHistory(2));
        }

        String prompt = templateConfig.buildIntentAnalyzerPrompt(userInput, historyText);
        logger.debug("[{}] 调用 LLM 进行意图识别", traceId);

        ChatResponse response;
        try {
            response = chatClient.prompt(prompt).call().chatResponse();
        } catch (Exception e) {
            logger.error("[{}] LLM 调用异常: {}", traceId, e.getMessage());
            throw new RuntimeException("模型调用失败: " + e.getMessage(), e);
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
            logger.debug("[{}] Token 用量 - 输入: {}, 输出: {}, 总计: {}",
                    traceId, usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
        }

        logger.debug("[{}] LLM 原始返回: {}", traceId, content);
        IntentResult result = parseIntentResult(content, traceId);

        agentState.setIntentType(result.getIntentType());
        agentState.setConfidenceScore(result.getScore());
        agentState.setRequiresClarification(result.getScore() < 0.5 || result.getIntentType() == IntentType.UNKNOWN);
        logger.info("[{}] 意图识别完成：意图={}, 置信度={}, 需澄清={}",
                traceId, agentState.getIntentType(), agentState.getConfidenceScore(), agentState.isRequiresClarification());

        return buildUpdateMap(agentState);
    }

    /**
     * 解析 LLM 返回的意图识别结果。
     *
     * @param content LLM 返回的原始内容（一定不为 null）
     * @param traceId 追踪 ID
     * @return IntentResult 对象，解析失败时抛出异常
     */
    private IntentResult parseIntentResult(String content, String traceId) {
        try {
            String jsonContent = content.trim();
            Matcher matcher = JSON_PATTERN.matcher(content);
            if (matcher.find()) {
                jsonContent = matcher.group(1).trim();
            }
            return objectMapper.readValue(jsonContent, IntentResult.class);
        } catch (JsonProcessingException e) {
            logger.error("[{}] JSON 解析失败，原始内容: {}", traceId, content, e);
            throw new RuntimeException("意图解析失败，模型返回格式错误", e);
        }
    }

    private void setUnknownIntent(AgentState agentState) {
        agentState.setIntentType(IntentType.UNKNOWN);
        agentState.setConfidenceScore(0.0);
        agentState.setRequiresClarification(true);
    }

    private Map<String, Object> buildUpdateMap(AgentState agentState) {
        Map<String, Object> updateMap = new HashMap<>();
        updateMap.put("agent_state", agentState);
        return updateMap;
    }
}