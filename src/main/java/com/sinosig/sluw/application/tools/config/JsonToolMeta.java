package com.sinosig.sluw.application.tools.config;

import java.util.*;

/**
 * JSON 工具元数据配置类。
 * <p>用于定义从 JSON 响应中提取数据、判断成功/失败以及字段语义映射的规则。</p>
 * <p>支持 JSON Pointer (RFC 6901) 语法定位字段。</p>
 *
 * @author SinoSig AI Team
 */
public class JsonToolMeta {

    /** 数据体所在的 JSON Pointer 路径，如 "/resultData"、"/data"，空字符串表示根节点 */
    private String dataPointer = "";

    /** 成功判断的 JSON Pointer 路径，如 "/Success"、"/code" */
    private String successPointer = "/Success";

    /** 成功时对应的期望值，如 "true"、"0"，若为空则仅判断字段为 true 或非 0 */
    private String successValue = "true";

    /** 错误信息的 JSON Pointer 路径 */
    private String messagePointer = "/message";

    /** 错误码的 JSON Pointer 路径 */
    private String errorCodePointer = "/errCode";

    /** 是否跳过成功/失败检查，适用于数据库查询等无状态码的场景 */
    private boolean skipSuccessCheck = false;

    /** 字段映射列表 */
    private final List<FieldMapping> fieldMappings = new ArrayList<>();

    // ==================== 链式配置方法 ====================

    public JsonToolMeta dataPointer(String dataPointer) {
        this.dataPointer = dataPointer;
        return this;
    }

    public JsonToolMeta successPointer(String pointer, String expectedValue) {
        this.successPointer = pointer;
        this.successValue = expectedValue;
        return this;
    }

    public JsonToolMeta messagePointer(String messagePointer) {
        this.messagePointer = messagePointer;
        return this;
    }

    public JsonToolMeta errorCodePointer(String errorCodePointer) {
        this.errorCodePointer = errorCodePointer;
        return this;
    }

    public JsonToolMeta skipSuccessCheck(boolean skip) {
        this.skipSuccessCheck = skip;
        return this;
    }

    public JsonToolMeta addField(String jsonPointer, String description) {
        fieldMappings.add(new FieldMapping(jsonPointer, description, null, null));
        return this;
    }

    public JsonToolMeta addField(String jsonPointer, String description, Map<String, String> valueMapping) {
        fieldMappings.add(new FieldMapping(jsonPointer, description, valueMapping, null));
        return this;
    }

    public JsonToolMeta addField(String jsonPointer, String description,
                                 Map<String, String> valueMapping, String nullValueReplacement) {
        fieldMappings.add(new FieldMapping(jsonPointer, description, valueMapping, nullValueReplacement));
        return this;
    }

    // ==================== Getters ====================

    public String getDataPointer() {
        return dataPointer;
    }

    public String getSuccessPointer() {
        return successPointer;
    }

    public String getSuccessValue() {
        return successValue;
    }

    public String getMessagePointer() {
        return messagePointer;
    }

    public String getErrorCodePointer() {
        return errorCodePointer;
    }

    public boolean isSkipSuccessCheck() {
        return skipSuccessCheck;
    }

    public List<FieldMapping> getFieldMappings() {
        return fieldMappings;
    }

    // ==================== 内部类：字段映射 ====================

    /**
     * 字段映射定义。
     */
    public static class FieldMapping {
        /** JSON Pointer 路径，相对于数据体节点 */
        private final String jsonPointer;
        /** 字段的中文描述 */
        private final String description;
        /** 值映射，key 为原始值，value 为显示文本，支持 "other" 作为默认映射 */
        private final Map<String, String> valueMapping;
        /** 字段值为 null 或缺失时的替代文本 */
        private final String nullValueReplacement;

        public FieldMapping(String jsonPointer, String description,
                            Map<String, String> valueMapping, String nullValueReplacement) {
            this.jsonPointer = jsonPointer;
            this.description = description;
            this.valueMapping = valueMapping;
            this.nullValueReplacement = nullValueReplacement;
        }

        public String getJsonPointer() {
            return jsonPointer;
        }

        public String getDescription() {
            return description;
        }

        public Map<String, String> getValueMapping() {
            return valueMapping;
        }

        public String getNullValueReplacement() {
            return nullValueReplacement;
        }
    }
}