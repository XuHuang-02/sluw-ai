package com.sinosig.sluw.application.tools.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 通用 JSON 语义转换器。
 * <p>将工具返回的 JSON 字符串转换为大模型易于理解的自然语言描述文本。</p>
 * <p>转换规则依据 {@link JsonToolMetaRegistry} 中注册的元数据配置。</p>
 *
 * @author SinoSig AI Team
 */
@Component
public class JsonSemanticConverter {

    private static final Logger logger = LoggerFactory.getLogger(JsonSemanticConverter.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    @Qualifier("jsonToolMetaMap")
    private Map<String, JsonToolMeta> toolMetaMap;

    /**
     * 将 JSON 字符串转换为语义描述文本。
     *
     * @param toolKey 工具唯一标识（与注册表中的 key 一致）
     * @param jsonStr 工具返回的原始 JSON 字符串
     * @return 自然语言描述文本，若转换失败则返回错误提示
     */
    public String convert(String toolKey, String jsonStr) {
        logger.debug("开始语义转换，toolKey={}", toolKey);

        JsonToolMeta meta = toolMetaMap.get(toolKey);
        if (meta == null) {
            logger.warn("未找到工具 [{}] 的元数据配置，返回原始 JSON", toolKey);
            return jsonStr;
        }

        try {
            JsonNode root = objectMapper.readTree(jsonStr);

            // 1. 判断成功/失败（除非配置了跳过）
            if (!meta.isSkipSuccessCheck()) {
                boolean success = evaluateSuccess(root, meta);
                if (!success) {
                    String errCode = getStringByPointer(root, meta.getErrorCodePointer(), "");
                    String message = getStringByPointer(root, meta.getMessagePointer(), "未知错误");
                    String errorMsg = String.format("操作失败。错误码：%s，提示信息：%s",
                            errCode.isEmpty() ? "无" : errCode, message);
                    logger.debug("工具 [{}] 返回失败状态，错误信息：{}", toolKey, errorMsg);
                    return errorMsg;
                }
            }

            // 2. 定位数据体
            JsonNode dataNode = root;
            String dataPointer = meta.getDataPointer();
            if (dataPointer != null && !dataPointer.isEmpty()) {
                dataNode = root.at(dataPointer);
                if (dataNode.isMissingNode()) {
                    logger.warn("工具 [{}] 数据体定位失败，dataPointer={}", toolKey, dataPointer);
                    return "操作成功，但数据节点定位失败，请联系管理员。";
                }
            }

            // 3. 遍历字段映射生成描述
            StringBuilder sb = new StringBuilder();
            for (JsonToolMeta.FieldMapping mapping : meta.getFieldMappings()) {
                JsonNode valueNode = dataNode.at(mapping.getJsonPointer());
                String rawValue;

                if (valueNode.isMissingNode() || valueNode.isNull()) {
                    String nullReplacement = mapping.getNullValueReplacement();
                    rawValue = (nullReplacement != null) ? nullReplacement : "未知";
                } else {
                    rawValue = valueNode.asText();
                }

                // 应用值映射（仅当 rawValue 不是由 nullReplacement 替换而来时，才进行值映射）
                String displayValue = rawValue;
                Map<String, String> valueMapping = mapping.getValueMapping();
                if (valueMapping != null && !valueMapping.isEmpty() && !valueNode.isNull() && !valueNode.isMissingNode()) {
                    displayValue = valueMapping.getOrDefault(rawValue,
                            valueMapping.getOrDefault("other", rawValue));
                }

                sb.append(String.format("- %s：%s\n", mapping.getDescription(), displayValue));
            }

            if (sb.length() == 0) {
                logger.warn("工具 [{}] 未配置字段映射，返回空描述", toolKey);
                return "操作成功，但未配置返回字段描述。";
            }

            String result = sb.toString();
            logger.debug("工具 [{}] 语义转换完成，结果长度：{}", toolKey, result.length());
            return result;

        } catch (Exception e) {
            logger.error("JSON 语义转换失败，toolKey={}, jsonStr={}", toolKey, jsonStr, e);
            return "数据解析失败，请联系管理员。";
        }
    }

    /**
     * 判断响应是否成功。
     */
    private boolean evaluateSuccess(JsonNode root, JsonToolMeta meta) {
        JsonNode successNode = root.at(meta.getSuccessPointer());
        if (successNode.isMissingNode()) {
            logger.debug("成功标志节点缺失，pointer={}", meta.getSuccessPointer());
            return false;
        }
        String expected = meta.getSuccessValue();
        if (expected == null || expected.isEmpty()) {
            // 若未指定期望值，则字段为 true 或非 0 数字时视为成功
            return successNode.asBoolean(false) || successNode.asInt(-1) != 0;
        }
        return successNode.asText().equals(expected);
    }

    /**
     * 根据 JSON Pointer 安全获取字符串值。
     */
    private String getStringByPointer(JsonNode root, String pointer, String defaultValue) {
        if (pointer == null || pointer.isEmpty()) {
            return defaultValue;
        }
        JsonNode node = root.at(pointer);
        return node.isMissingNode() ? defaultValue : node.asText(defaultValue);
    }
}