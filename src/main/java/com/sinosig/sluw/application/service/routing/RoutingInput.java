package com.sinosig.sluw.application.service.routing;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.core.JsonParser;
import java.util.*;

/** Missing groups are unknown; explicit null row fields represent SQL NULL. */
public final class RoutingInput {
    public static final String VERSION = "DEAL_ISSUE_V1";
    private static final ObjectMapper JSON = new ObjectMapper()
        .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
        .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
    private static final Set<String> GROUPS = ordered("currentErrors", "historyErrors", "lwmission", "lbmission", "lwnotepad", "autoallotbyerr");
    private static final Set<String> ERROR_FIELDS = ordered("uwrulecode", "insuredno", "noFailedRules", "lettertype", "peitem", "positivesign", "autoflag");
    private static final Set<String> HISTORY_FIELDS = ordered("uwno", "uwrulecode", "insuredno", "lettertype", "peitem", "positivesign", "autoflag");
    private static final Set<String> TASK_FIELDS = ordered("activityid", "lastoperator");
    private static final Set<String> NOTE_FIELDS = ordered("noteflag");
    private static Set<String> ordered(String... values) {
        return Collections.unmodifiableSet(new LinkedHashSet<>(List.of(values)));
    }
    /** Callers may customize their own copy, never the parser's shared configuration. */
    public static ObjectMapper newJsonMapper() { return JSON.copy(); }
    public static String writeJson(Object value) throws com.fasterxml.jackson.core.JsonProcessingException {
        return JSON.writeValueAsString(value);
    }
    private final JsonNode root;
    private RoutingInput(JsonNode root) { this.root = root; }
    public static RoutingInput parse(String text) {
        if (text == null || text.isBlank() || text.length() > 60000) throw new IllegalArgumentException("请输入不超过60000字符的完整JSON。");
        try {
            JsonNode root = JSON.readTree(text);
            var allowed = new LinkedHashSet<>(GROUPS); allowed.addAll(List.of("uwno", "contno", "completeness"));
            fields(root, allowed, false);
            if (!root.path("contno").isTextual() || root.path("contno").asText().isBlank())
                throw new IllegalArgumentException("请填写contno。");
            JsonNode batchNode=root.path("uwno");
            if (!batchNode.isIntegralNumber() || !batchNode.canConvertToInt() || batchNode.asInt()<1)
                throw new IllegalArgumentException("顶层uwno必须为当前批次的正整数。");
            fields(root.path("completeness"), GROUPS, false);
            for (String group : GROUPS) {
                JsonNode flag = root.path("completeness").get(group), rows = root.get(group);
                if (flag != null && !flag.isNull() && !flag.isBoolean()) throw new IllegalArgumentException("完整性必须为布尔值或null。");
                if (rows == null || rows.isNull()) {
                    if (flag != null && flag.asBoolean()) throw new IllegalArgumentException(group + "声明完整时必须提供数组。");
                    continue;
                }
                if (!rows.isArray() || rows.size() > 3000) throw new IllegalArgumentException(group + "必须为数组，且不超过3000条。");
                for (JsonNode row : rows) {
                    if (group.equals("autoallotbyerr")) {
                        if (!row.isNull() && !row.isTextual()) throw new IllegalArgumentException("排除规则编码必须为字符串或null。");
                        continue;
                    }
                    Set<String> names = group.endsWith("Errors") ? errorFields(group) : group.equals("lwnotepad") ? NOTE_FIELDS : TASK_FIELDS;
                    fields(row, names, true);
                    for (String name : names) {
                        JsonNode value = row.get(name);
                        if (name.equals("uwno")) {
                            if (!value.isIntegralNumber() || !value.canConvertToInt() || value.asInt() < 1) throw new IllegalArgumentException("uwno必须为正整数。");
                        } else if (name.equals("noFailedRules")) {
                            if (!value.isBoolean()) throw new IllegalArgumentException("noFailedRules必须为布尔值，不能猜测或用null代替未知。");
                        } else if (!value.isNull() && (!value.isTextual() || value.asText().isEmpty())) {
                            throw new IllegalArgumentException("记录字段须为非空字符串或null；未知字段请补齐，数据库空值用null。");
                        }
                    }
                }
            }
            var input = new RoutingInput(root);
            var current = input.rows("currentErrors");
            int batch = root.path("uwno").asInt();
            if (input.rows("historyErrors").stream().anyMatch(r -> r.path("uwno").asInt() >= batch))
                throw new IllegalArgumentException("历史批次必须小于顶层当前批次uwno。");
            if (current.isEmpty() && input.complete("currentErrors"))
                throw new IllegalArgumentException("dealIssue试验需要已有最新核保记录；当前记录为空时无法满足入口前提。");
            return input;
        } catch (IllegalArgumentException e) { throw e; }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalArgumentException("JSON格式不合要求，请使用新模板，检查重复字段及字段类型。", e); }
    }
    private static Set<String> errorFields(String group) {
        if(group.equals("currentErrors"))return ERROR_FIELDS;
        return HISTORY_FIELDS;
    }
    private static void fields(JsonNode node, Set<String> allowed, boolean required) {
        if (!node.isObject()) throw new IllegalArgumentException("记录和完整性说明必须为JSON对象。");
        node.fieldNames().forEachRemaining(k -> { if (!allowed.contains(k)) throw new IllegalArgumentException("包含不支持的字段：" + k); });
        if (required) for (String k : allowed) if (!node.has(k)) throw new IllegalArgumentException("记录缺少字段：" + k + "；明确数据库空值才可填写null。");
    }
    public int currentBatch() { return root.path("uwno").asInt(); }
    public boolean complete(String group) { return root.path("completeness").path(group).asBoolean(false); }
    public List<JsonNode> rows(String group) {
        var result = new ArrayList<JsonNode>(); var rows = root.path(group);
        if (rows.isArray()) rows.forEach(result::add); return result;
    }
}
