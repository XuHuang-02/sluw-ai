package com.sinosig.sluw.application.dto;

/**
 * 用户意图枚举类型。
 * <p>定义系统支持的所有意图类别，用于路由和业务处理。</p>
 *
 * @author SinoSig AI Team
 */
public enum IntentType {

    /** 合规检核规则生成*/
    RULE_GENERATION,   // 新增：合规检核规则生成

    /** 知识查询意图：用户询问保险条款、政策、流程等知识性问题 */
    KNOWLEDGE_QUERY,

    /** 工具执行意图：用户需要执行具体操作，如查询保单、报案、获取数据等 */
    TOOL_EXECUTION,

    /** 闲聊意图：用户进行问候、闲聊等非业务性对话 */
    CHIT_CHAT,

    /** 澄清意图：置信度不足或意图未知，需要进一步引导用户 */
    CLARIFICATION,

    /** 角色意图：询问人员身份，符合角色定位定义**/
    ROLE,

    /** 未知意图：系统无法识别用户意图 */
    UNKNOWN;

    /**
     * 将字符串转换为对应的 IntentType。
     *
     * @param value 意图字符串（大小写不敏感）
     * @return 对应的 IntentType，若无法匹配则返回 UNKNOWN
     */
    public static IntentType fromString(String value) {
        try {
            return IntentType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}