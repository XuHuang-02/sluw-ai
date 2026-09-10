package com.sinosig.sluw.application.service;

import com.sinosig.sluw.application.config.PromptConfig;
import com.sinosig.sluw.application.client.RagFlowClient;
import com.sinosig.sluw.application.dto.ChatRequest;
import com.sinosig.sluw.application.dto.ChatResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 知识库服务
 * 实现ICAC智能小助手的核心业务逻辑
 *
 * @author
 * @version 1.0
 */
@Service
public class KnowledgeService {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeService.class);

    private final ChatClient chatClient;
    private final RagFlowClient ragFlowClient;
    private final PromptConfig promptConfig;
    private final ChatMemory chatMemory;

    /**
     * 构造函数注入
     *
     * @param chatClient    ChatClient实例，用于调用AI模型
     * @param ragFlowClient RAGFlow客户端，用于知识库检索
     * @param promptConfig  提示词配置，包含润色和总结模板
     * @param chatMemory    会话记忆存储，用于多轮对话上下文管理
     */
    public KnowledgeService(
            ChatClient chatClient,
            RagFlowClient ragFlowClient,
            PromptConfig promptConfig,
            ChatMemory chatMemory) {

        this.chatClient = chatClient;
        this.ragFlowClient = ragFlowClient;
        this.promptConfig = promptConfig;
        this.chatMemory = chatMemory;
    }


    /**
     * 获取AI助手的回答
     * 支持多轮对话与上下文管理
     *
     * @param request 聊天请求对象，包含用户ID、会话ID和问题内容
     * @return 聊天响应对象，包含最终答案和会话信息
     */
    public ChatResponse getAnswer(ChatRequest request) {
        long startTime = System.currentTimeMillis();
        // 获取会话标识
        String conversationId = request.getConversationId();
        // 获取用户标识
        String userId = request.getUserId();

        logger.info("========== 开始处理用户问题 ==========");
        logger.info("用户ID: {}, 会话ID: {}", userId, conversationId);
        logger.info("用户问题: {}", request.getQuestion());
        logger.info("A/B测试配置 - 使用润色: {}, 润色阶段使用上下文: {}",
                request.isUseRefiner(),
                request.isUseRefinerMemory());

        // 获取会话历史（最近5条消息）
        List<Message> history = chatMemory.get(conversationId);

        // 1. 先声明变量并提供默认值
        String historyStr = "[]";
        // 2. 如果历史不为空，再进行格式化
        // 将历史消息列表格式化为字符串，供模板使用
        // 格式: [UserMessage: 内容, AssistantMessage: 内容, ...]
        if (history != null && !history.isEmpty()) {
            historyStr = history.stream()
                    .map(msg -> msg.getClass().getSimpleName()+ ",: " + msg.getMessageType().getValue() + ",: " + msg.getText())
                    .collect(Collectors.joining(", "));
        }

        // 步骤1: A/B测试 - 决定是否润色问题
        String questionToSearch = request.getQuestion();
        if (request.isUseRefiner()) {
            logger.info("--- 执行问题润色 ---");

            // 构建润色提示词模板
            String refinerTemplate = promptConfig.getQuestionRefiner().getTemplate();

            // 根据开关决定如何处理历史上下文
            String historyForRefiner = "无";
            if (request.isUseRefinerMemory()) {
                historyForRefiner = historyStr;
                logger.info("润色阶段启用上下文（历史消息数量: {}）", history.size());
            } else {
                logger.info("润色阶段禁用上下文");
            }

            // 动态注入参数到模板
            // 注意：这里严格按照模板中的占位符名称进行替换
            String refinerPromptText = refinerTemplate
                    .replace("{History}", historyForRefiner) // 使用根据开关决定的值
                    .replace("{UseHistory}", String.valueOf(request.isUseRefinerMemory()))
                    .replace("{userQuestion}", request.getQuestion());

            // 创建润色提示词 - 支持上下文使用开关
            Prompt refinerPrompt = new Prompt(new UserMessage(refinerPromptText));

            logger.info("润色提示词全文: {}", refinerPromptText);

            // 使用ChatClient进行问题润色
            questionToSearch = chatClient.prompt(refinerPrompt)
                    .call()
                    .content();
            logger.info("润色后的问题: {}", questionToSearch);
        } else {
            logger.info("--- 跳过问题润色，直接使用原始问题 ---");
        }

        // 步骤2: 知识库检索
        logger.info("--- 开始知识库检索 ---");
        logger.info("检索问题: {}", questionToSearch);
        String retrievedContext = ragFlowClient.retrieve(questionToSearch);
        logger.info("检索完成");

        // 步骤3: 大模型总结归纳
        logger.info("--- 开始生成最终答案 ---");

        // 构建总结提示词
        String finalSummarizerTemplate = promptConfig.getFinalSummarizer().getTemplate();

        // 根据开关决定是否在最终提示词中包含历史信息
        String historyForSummary = "无";
        if (request.isUseRefinerMemory()) {
            historyForSummary = historyStr;
            logger.info("总结阶段启用上下文（历史消息数量: {}）", history.size());
        } else {
            logger.info("总结阶段禁用上下文");
        }

        String finalPromptText = finalSummarizerTemplate
                .replace("{History}", historyForSummary)
                .replace("{UseHistory}", String.valueOf(request.isUseRefinerMemory()))
                .replace("{userQuestion}", request.getQuestion())
                .replace("{retrievedContext}", retrievedContext);

        // 根据开关决定是否将历史消息作为独立的Message对象添加到Prompt中
        List<Message> finalMessages;
        if (request.isUseRefinerMemory()) {
            // 启用上下文：合并历史消息和当前用户问题
            finalMessages = new ArrayList<>(history);
            finalMessages.add(new UserMessage(finalPromptText));
        } else {
            // 禁用上下文：仅使用当前构建的提示词文本作为用户消息
            finalMessages = Collections.singletonList(new UserMessage(finalPromptText));
        }

        Prompt finalPrompt = new Prompt(finalMessages);
        logger.debug("最终提示词全文: {}", finalPrompt.getInstructions());

        // 调用ChatClient生成最终答案
        String finalAnswer = chatClient.prompt(finalPrompt)
                .call()
                .content();
        logger.info("答案生成完成");

        // 更新会话记忆
        // 无论是否在AI推理中使用历史，都应记录本次对话，以保证对话的完整性和前端展示
        chatMemory.add(conversationId, new UserMessage(request.getQuestion()));
        chatMemory.add(conversationId, new AssistantMessage(finalAnswer));

        // 步骤4: 组装响应
        ChatResponse response = new ChatResponse(finalAnswer, questionToSearch, retrievedContext, conversationId);
        long processingTime = System.currentTimeMillis() - startTime;
        response.setProcessingTime(processingTime);

        logger.info("========== 处理完成，耗时: {} ms ==========", processingTime);

        return response;
    }
}