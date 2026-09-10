package com.sinosig.sluw.application.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * 意图识别结果封装类。
 * <p>用于解析 LLM 返回的 JSON 格式意图识别结果。</p>
 *
 * @author SinoSig AI Team
 */
public class IntentResult {

    /** 识别出的意图类型 */
    private final IntentType intentType;

    /** 置信度分数，范围 0.0-1.0 */
    private final double score;

    /**
     * 构造函数，用于 Jackson 反序列化。
     *
     * @param intent 意图字符串
     * @param score  置信度分数
     */
    @JsonCreator
    public IntentResult(@JsonProperty("intent") String intent, @JsonProperty("score") double score) {
        this.intentType = IntentType.fromString(intent);
        this.score = score;
    }

    public IntentType getIntentType() {
        return intentType;
    }

    public double getScore() {
        return score;
    }
}