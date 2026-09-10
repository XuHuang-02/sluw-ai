package com.sinosig.sluw.application.dto;

/**
 * 聊天响应数据传输对象
 * 用于返回AI助手的回答及相关调试信息
 *
 * @author
 * @version 1.0
 */
public class ChatResponse {

    /**
     * AI助手生成的最终答案
     * 必填字段
     */
    private String answer;

    /**
     * 润色后的问题（用于调试和展示）
     * 可选字段，当useRefiner为true时有值
     */
    private String refinedQuestion;

    /**
     * 从知识库检索到的上下文内容（用于调试和展示）
     * 可选字段
     */
    private String retrievedContext;

    /**
     * 处理耗时（毫秒）
     * 用于性能监控
     */
    private long processingTime;

    /**
     * 会话ID
     * 必填字段，不能为空
     */
    private String conversationId;

    /**
     * 构造函数
     * @param answer 最终答案
     * @param refinedQuestion 润色后的问题
     * @param retrievedContext 检索到的上下文
     * @param conversationId 会话ID
     */
    public ChatResponse(String answer, String refinedQuestion, String retrievedContext, String conversationId) {
        this.answer = answer;
        this.refinedQuestion = refinedQuestion;
        this.retrievedContext = retrievedContext;
        this.conversationId = conversationId;

    }

    /**
     * 获取最终答案
     * @return 答案字符串
     */
    public String getAnswer() {
        return answer;
    }

    /**
     * 设置最终答案
     * @param answer 答案字符串
     */
    public void setAnswer(String answer) {
        this.answer = answer;
    }

    /**
     * 获取润色后的问题
     * @return 润色后的问题字符串，可能为null
     */
    public String getRefinedQuestion() {
        return refinedQuestion;
    }

    /**
     * 设置润色后的问题
     * @param refinedQuestion 润色后的问题字符串
     */
    public void setRefinedQuestion(String refinedQuestion) {
        this.refinedQuestion = refinedQuestion;
    }

    /**
     * 获取检索到的上下文
     * @return 上下文字符串，可能为null
     */
    public String getRetrievedContext() {
        return retrievedContext;
    }

    /**
     * 设置检索到的上下文
     * @param retrievedContext 上下文字符串
     */
    public void setRetrievedContext(String retrievedContext) {
        this.retrievedContext = retrievedContext;
    }

    /**
     * 获取处理耗时
     * @return 耗时毫秒数
     */
    public long getProcessingTime() {
        return processingTime;
    }

    /**
     * 设置处理耗时
     * @param processingTime 耗时毫秒数
     */
    public void setProcessingTime(long processingTime) {
        this.processingTime = processingTime;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }
}
