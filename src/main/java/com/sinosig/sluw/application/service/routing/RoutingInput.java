package com.sinosig.sluw.application.service.routing;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.core.JsonParser;
import java.util.*;

/** Missing groups are unknown; explicit null row fields represent SQL NULL. */
public final class RoutingInput {
    public static final String VERSION = "DEAL_ISSUE_V1";
    public static final ObjectMapper JSON = new ObjectMapper()
        .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
        .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
        .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
    private static final Set<String> GROUPS = Set.of("currentErrors", "historyErrors", "lwmission", "lbmission", "lwnotepad", "autoallotbyerr");
    private static final Set<String> ERROR_FIELDS = Set.of("uwno", "uwrulecode", "insuredno", "uwerror", "lettertype", "peitem", "positivesign", "autoflag");
    private final JsonNode root;
    private RoutingInput(JsonNode root) { this.root = root; }
    public static RoutingInput parse(String text) {
        if (text == null || text.isBlank() || text.length() > 60000) throw new IllegalArgumentException("请输入不超过60000字符的完整JSON。");
        try {
            JsonNode root = JSON.readTree(text);
            var allowed = new HashSet<>(GROUPS); allowed.addAll(Set.of("schemaVersion", "contno", "completeness"));
            fields(root, allowed, false);
            if (!VERSION.equals(root.path("schemaVersion").asText()) || !root.path("contno").isTextual() || root.path("contno").asText().isBlank())
                throw new IllegalArgumentException("请使用DEAL_ISSUE_V1模板并填写contno，旧实验模板不再用于选路。");
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
                    Set<String> names = group.endsWith("Errors") ? errorFields(group) : group.equals("lwnotepad") ? Set.of("noteflag") : Set.of("activityid", "lastoperator");
                    fields(row, names, true);
                    for (String name : names) {
                        JsonNode value = row.get(name);
                        if (name.equals("uwno")) {
                            if (!value.isIntegralNumber() || !value.canConvertToInt() || value.asInt() < 1) throw new IllegalArgumentException("uwno必须为正整数。");
                        } else if (!value.isNull() && (!value.isTextual() || value.asText().isEmpty())) {
                            throw new IllegalArgumentException("记录字段须为非空字符串或null；未知字段请补齐，数据库空值用null。");
                        }
                    }
                }
            }
            var input = new RoutingInput(root);
            var current = input.rows("currentErrors");
            if (!current.isEmpty()) {
                int batch = current.get(0).path("uwno").asInt();
                if (current.stream().anyMatch(r -> r.path("uwno").asInt() != batch)
                    || input.rows("historyErrors").stream().anyMatch(r -> r.path("uwno").asInt() >= batch))
                    throw new IllegalArgumentException("当前记录须属于同一最新批次，历史批次必须小于当前批次。");
            } else if (input.complete("currentErrors")) {
                throw new IllegalArgumentException("dealIssue试验需要已有最新核保记录；当前记录为空时无法满足入口前提。");
            }
            return input;
        } catch (IllegalArgumentException e) { throw e; }
        catch (Exception e) { throw new IllegalArgumentException("JSON格式不合要求，请使用新模板，检查重复字段及字段类型。"); }
    }
    private static Set<String> errorFields(String group) {
        if(group.equals("currentErrors"))return ERROR_FIELDS;
        var fields=new HashSet<>(ERROR_FIELDS);fields.remove("uwerror");return fields;
    }
    private static void fields(JsonNode node, Set<String> allowed, boolean required) {
        if (!node.isObject()) throw new IllegalArgumentException("记录和完整性说明必须为JSON对象。");
        node.fieldNames().forEachRemaining(k -> { if (!allowed.contains(k)) throw new IllegalArgumentException("包含不支持的字段：" + k); });
        if (required) for (String k : allowed) if (!node.has(k)) throw new IllegalArgumentException("记录缺少字段：" + k + "；明确数据库空值才可填写null。");
    }
    public boolean complete(String group) { return root.path("completeness").path(group).asBoolean(false); }
    public List<JsonNode> rows(String group) {
        var result = new ArrayList<JsonNode>(); var rows = root.path(group);
        if (rows.isArray()) rows.forEach(result::add); return result;
    }
}
