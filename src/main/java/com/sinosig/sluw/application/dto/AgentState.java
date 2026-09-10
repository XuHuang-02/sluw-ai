package com.sinosig.sluw.application.dto;

import java.io.Serializable;
import java.util.*;

/**
 * 智能体状态对象。
 * <p>在工作流节点之间传递的核心数据对象，包含用户输入、意图、上下文、响应历史等信息。</p>
 * <p>注意：context 中只应存放基本类型、字符串、列表、映射等可序列化类型，避免 Redis 反序列化失败。</p>
 *
 * @author SinoSig AI Team
 */
public class AgentState implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户当前输入（可能经过润色） */
    private String userInput;

    /** 用户原始输入（润色前） */
    private String originalInput;

    /** 识别出的意图类型 */
    private IntentType intentType;

    /** 意图置信度分数（0.0-1.0） */
    private Double confidenceScore;

    /** 是否需要澄清（当置信度低于阈值或意图为 UNKNOWN 时设为 true） */
    private boolean requiresClarification;

    /** 上下文数据，用于节点间传递临时信息（必须可序列化） */
    private Map<String, Object> context;

    /** 最终响应内容 */
    private String response;

    /** 对话历史记录，每条记录包含 role 和 content */
    private List<Map<String, String>> history;

    /** 是否使用问题润色（A/B测试开关） */
    private boolean useRefiner = true;

    /** 是否在润色时使用历史上下文（A/B测试开关） */
    private boolean useRefinerMemory = true;

    /** 累计输入 Token 数 */
    private int totalPromptTokens = 0;
    /** 累计输出 Token 数 */
    private int totalCompletionTokens = 0;
    /** 累计总 Token 数 */
    private int totalTokens = 0;

    public AgentState() {
        this.context = new HashMap<>();
        this.history = new ArrayList<>();
        this.requiresClarification = false;
    }

    public AgentState(String userInput) {
        this.userInput = userInput;
        this.originalInput = userInput;
        this.context = new HashMap<>();
        this.history = new ArrayList<>();
        this.requiresClarification = false;
    }

    // ========== Getters and Setters ==========
    public String getUserInput() { return userInput; }
    public void setUserInput(String userInput) { this.userInput = userInput; }

    public String getOriginalInput() { return originalInput; }
    public void setOriginalInput(String originalInput) { this.originalInput = originalInput; }

    public IntentType getIntentType() { return intentType; }
    public void setIntentType(IntentType intentType) { this.intentType = intentType; }

    public Double getConfidenceScore() { return confidenceScore; }
    public void setConfidenceScore(Double confidenceScore) { this.confidenceScore = confidenceScore; }

    public boolean isRequiresClarification() { return requiresClarification; }
    public void setRequiresClarification(boolean requiresClarification) { this.requiresClarification = requiresClarification; }

    public Map<String, Object> getContext() { return context; }
    public void setContext(Map<String, Object> context) { this.context = context; }

    public String getResponse() { return response; }
    public void setResponse(String response) { this.response = response; }

    public List<Map<String, String>> getHistory() { return history; }
    public void setHistory(List<Map<String, String>> history) { this.history = history; }

    public boolean isUseRefiner() { return useRefiner; }
    public void setUseRefiner(boolean useRefiner) { this.useRefiner = useRefiner; }

    public boolean isUseRefinerMemory() { return useRefinerMemory; }
    public void setUseRefinerMemory(boolean useRefinerMemory) { this.useRefinerMemory = useRefinerMemory; }

    // Token 统计字段的 getters 和 setters
    public int getTotalPromptTokens() { return totalPromptTokens; }
    public void setTotalPromptTokens(int totalPromptTokens) { this.totalPromptTokens = totalPromptTokens; }

    public int getTotalCompletionTokens() { return totalCompletionTokens; }
    public void setTotalCompletionTokens(int totalCompletionTokens) { this.totalCompletionTokens = totalCompletionTokens; }

    public int getTotalTokens() { return totalTokens; }
    public void setTotalTokens(int totalTokens) { this.totalTokens = totalTokens; }

    /**
     * 添加一条对话记录（原子操作，由 Service 层在成功生成回复后调用）。
     *
     * @param role    角色（user/assistant）
     * @param content 内容
     */
    public void addHistory(String role, String content) {
        Map<String, String> entry = new HashMap<>();
        entry.put("role", role);
        entry.put("content", content);
        history.add(entry);
    }

    /**
     * 获取最近 N 轮对话历史。
     *
     * @param maxTurns 最大轮数
     * @return 历史列表（按时间顺序，可能少于 maxTurns）
     */
    public List<Map<String, String>> getRecentHistory(int maxTurns) {
        if (history == null || history.isEmpty()) {
            return Collections.emptyList();
        }
        int start = Math.max(0, history.size() - maxTurns);
        return history.subList(start, history.size());
    }

    /**
     * 累积 Token 用量。
     * <p>Spring AI 的 Usage 接口提供 prompt/completion/total tokens 三个指标。</p>
     *
     * @param usage Spring AI 的 Usage 对象
     */
    public void accumulateTokenUsage(org.springframework.ai.chat.metadata.Usage usage) {
        if (usage != null) {
            this.totalPromptTokens += usage.getPromptTokens();
            this.totalCompletionTokens += usage.getCompletionTokens();
            this.totalTokens += usage.getTotalTokens();
        }
    }
}