package com.sinosig.sluw.application.dto;

/**
 * 聊天请求数据传输对象。
 * <p>用于接收用户的提问和A/B测试参数。</p>
 *
 * @author SinoSig AI Team
 */
public class ChatRequest {

    /** 用户提出的问题，必填字段，不能为空 */
    private String question;
    private boolean issueSubmissionTrial;
    public boolean isIssueSubmissionTrial() { return issueSubmissionTrial; }
    public void setIssueSubmissionTrial(boolean value) { issueSubmissionTrial = value; }


    /** A/B测试开关：true表示使用问题润色，false表示不使用润色直接检索，默认值为true */
    private boolean useRefiner = true;

    /** A/B测试开关：true表示问题润色引入历史上下文，false表示不引入历史上下文直接润色，默认值为true */
    private boolean useRefinerMemory = true;

    /** 会话ID，用于多轮对话 */
    private String conversationId;

    /** 用户ID，用于区分不同用户 */
    private String userId;

    /** 消息ID */
    private String messageId;

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public boolean isUseRefiner() {
        return useRefiner;
    }

    public void setUseRefiner(boolean useRefiner) {
        this.useRefiner = useRefiner;
    }

    public boolean isUseRefinerMemory() {
        return useRefinerMemory;
    }

    public void setUseRefinerMemory(boolean useRefinerMemory) {
        this.useRefinerMemory = useRefinerMemory;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    /**
     * 验证请求是否有效。
     *
     * @return 有效返回true，无效返回false
     */
    public boolean isValid() {
        return question != null && !question.trim().isEmpty();
    }
}