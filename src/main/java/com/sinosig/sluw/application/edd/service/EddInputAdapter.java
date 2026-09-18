package com.sinosig.sluw.application.edd.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.*;

/** Pure JSON conversion. No Spring, database, network, model or business decisions. */
public final class EddInputAdapter {
    private static final int MAX_BYTES = 16 * 1024 * 1024;
    private static final Set<String> ROLES = Set.of("policyholder", "insured", "beneficiary");
    private static final Set<String> INPUT_FIELDS = Set.of("subject_id", "context", "analysis_as_of", "raw_tables", "coverage", "approved_rating_rules_available", "codebook_version");
    private static final Set<String> STATUSES = Set.of("succeeded", "not_queried", "failed", "permission_denied", "not_provided");
    private final ObjectMapper mapper;
    private final JsonNode dictionary;
    private final JsonNode paymentLabels;

    public record Context(String snapshotId, boolean synthetic, String trigger, String sourceId,
                          String extractedAt, String dictionaryVersion, String queryVersion) {}
    public record Issue(String code, String path, String message) {}
    public record Interpretation(String factId, String field, String rawCode, String label, String status) {}
    public record RowProvenance(String factId, String inputPointer, ObjectNode originalRow) {
        public RowProvenance { originalRow = originalRow.deepCopy(); }
        @Override public ObjectNode originalRow() { return originalRow.deepCopy(); }
    }
    public record Adaptation(ObjectNode request, List<Issue> issues,
                             List<Interpretation> interpretations, List<RowProvenance> provenance) {
        public Adaptation {
            request = request.deepCopy(); issues = List.copyOf(issues);
            interpretations = List.copyOf(interpretations); provenance = List.copyOf(provenance);
        }
        @Override public ObjectNode request() { return request.deepCopy(); }
    }
    public static final class InputException extends IllegalArgumentException {
        private final String code;
        private final String path;
        InputException(String code, String path, String safeMessage) {
            super(safeMessage); this.code = code; this.path = path;
        }
        public String code() { return code; }
        public String path() { return path; }
    }

    public EddInputAdapter() {
        mapper = new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
        try (var stream = EddInputAdapter.class.getResourceAsStream("/edd/core/dictionary-v1.json")) {
            if (stream == null) throw new IllegalStateException("EDD core dictionary missing");
            JsonNode resource = mapper.readTree(stream);
            dictionary = resource.get("tables");
            paymentLabels = resource.get("paymode_labels");
        } catch (IOException e) { throw new IllegalStateException("EDD core dictionary unavailable", e); }
    }

    /** Caller supplies provenance metadata explicitly; unknown source times remain null. */
    public Adaptation adaptCoreTables(String json, Context ctx) {
        if (json == null) throw error("INVALID_JSON", "", "JSON input required");
        if (json.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw error("PAYLOAD_TOO_LARGE", "", "JSON exceeds 16 MiB");
        JsonNode parsed;
        try { parsed = mapper.readTree(json); }
        catch (IOException e) { throw error("INVALID_JSON", "", "Invalid JSON or duplicate object key"); }
        ObjectNode input = object(parsed, "");
        requireKeys(input, INPUT_FIELDS, "");
        if (ctx == null) throw error("INVALID_REQUEST", "/metadata", "Metadata required");
        id(ctx.snapshotId(), "/metadata/snapshot_id"); id(ctx.sourceId(), "/metadata/source_id");
        nonempty(ctx.trigger(), "/metadata/trigger");
        if (ctx.extractedAt() != null) time(ctx.extractedAt(), "/metadata/extracted_at");
        if (ctx.dictionaryVersion() != null) nonempty(ctx.dictionaryVersion(), "/metadata/dictionary_version");
        if (ctx.queryVersion() != null) nonempty(ctx.queryVersion(), "/metadata/query_version");
        String subject = text(input.get("subject_id"), "/subject_id"); id(subject, "/subject_id");
        String asOf = text(input.get("analysis_as_of"), "/analysis_as_of"); time(asOf, "/analysis_as_of");
        ObjectNode context = object(input.get("context"), "/context");
        requireKeys(context, Set.of("policy_id", "role"), "/context");
        String role = text(context.get("role"), "/context/role");
        if (!ROLES.contains(role)) throw error("INVALID_REQUEST", "/context/role", "Unknown customer role");
        JsonNode policy = context.get("policy_id");
        if (policy != null && !policy.isNull()) id(text(policy, "/context/policy_id"), "/context/policy_id");
        JsonNode approved = input.get("approved_rating_rules_available");
        if (approved != null && (!approved.isBoolean() || approved.booleanValue()))
            throw error("INVALID_REQUEST", "/approved_rating_rules_available", "Caller cannot approve rating rules");
        JsonNode codebook = input.get("codebook_version");
        if (codebook != null && !codebook.isNull()) text(codebook, "/codebook_version");
        List<Issue> issues = new ArrayList<>();
        List<Interpretation> interpretations = new ArrayList<>();
        List<RowProvenance> provenance = new ArrayList<>();
        ObjectNode request = mapper.createObjectNode();
        request.put("schema_version", "1.0").put("synthetic", ctx.synthetic()).put("snapshot_id", ctx.snapshotId())
                .put("analysis_as_of", asOf).putNull("parent_analysis_id");
        request.putObject("subject").put("customer_id", subject).putNull("display_name");
        request.putObject("context").put("role", role).put("policy_id", policy == null || policy.isNull() ? null : policy.textValue()).put("trigger", ctx.trigger());
        ObjectNode coverage = normalizeCoverage(input.get("coverage")); request.set("coverage", coverage);
        ArrayNode snapshots = request.putArray("core_snapshots");
        for (String name : List.of("events", "risk_records", "manual_excerpts", "attachments", "conflicts")) request.putArray(name);
        request.putObject("rule_context").putNull("rule_package_id").putNull("expected_version");
        ObjectNode tables = object(input.get("raw_tables"), "/raw_tables");
        Set<String> seenTables = new HashSet<>(); Map<String, Integer> duplicateRows = new HashMap<>();
        Iterator<Map.Entry<String, JsonNode>> iterator = tables.fields();
        while (iterator.hasNext()) {
            var tableEntry = iterator.next();
            String rawTable = tableEntry.getKey(); String table = normalizeTable(rawTable);
            String tablePath = "/raw_tables/" + pointer(rawTable);
            if (!seenTables.add(table)) throw error("INVALID_REQUEST", tablePath, "Duplicate case-insensitive table");
            JsonNode definition = dictionary.get(table);
            if (definition == null) throw error("INVALID_REQUEST", tablePath, "Unsupported core table; explicit mapping required");
            JsonNode rows = tableEntry.getValue();
            if (!rows.isArray()) throw error("INVALID_REQUEST", tablePath, "Table rows must be an array");
            for (int i = 0; i < rows.size(); i++) {
                if (snapshots.size() >= 100000) throw error("PAYLOAD_TOO_LARGE", tablePath, "Snapshot count exceeds 100000");
                String path = tablePath + "/" + i;
                ObjectNode original = object(rows.get(i), path);
                ObjectNode values = normalizeRow(original, definition, path, issues);
                ObjectNode key = mapper.createObjectNode();
                for (JsonNode keyField : definition.get("key_fields")) {
                    String field = keyField.asText(); JsonNode value = values.get(field);
                    if (value == null || value.isNull() || value.asText().isBlank())
                        issues.add(new Issue("SOURCE_KEY_INCOMPLETE", path + "/" + field, "Source key field missing; row retained"));
                    else key.put(field, value.asText());
                }
                // Include full normalized row to distinguish updates; identical duplicates remain separate facts.
                String hash = digest(table + ":" + values);
                int occurrence = duplicateRows.merge(table + hash, 1, Integer::sum);
                String factId = "CORE-" + table + "-" + hash.substring(0, 24) + "-" + occurrence;
                ObjectNode snapshot = snapshots.addObject();
                snapshot.put("fact_id", factId).put("snapshot_type", definition.get("snapshot_type").asText());
                snapshot.set("values", values);
                ObjectNode source = snapshot.putObject("source");
                source.put("source_id", ctx.sourceId()).put("source_kind", ctx.synthetic() ? "synthetic" : "core_json")
                        .put("table", "SLISDATA." + table).put("extracted_at", ctx.extractedAt())
                        .put("dictionary_version", ctx.dictionaryVersion()).put("query_version", ctx.queryVersion());
                source.set("key", key); ArrayNode fieldNames = source.putArray("fields");
                values.fieldNames().forEachRemaining(fieldNames::add);
                provenance.add(new RowProvenance(factId, path, original));
                interpret(table, factId, values, path, issues, interpretations);
            }
        }
        for (String table : List.of("LCCONT", "LCPOL", "LCDUTY", "LCPREM", "LCGET", "LCCONTSTATE", "T_SLIS_LC_EXPANSION"))
            if (!seenTables.contains(table)) issues.add(new Issue("TABLE_NOT_PROVIDED", "/raw_tables/" + table, "Table not provided; not proof of no records"));
        for (String group : List.of("actual_payments", "preservation_events", "individual_claims", "customer_master", "beneficiary_relationships", "risk_history", "judicial", "blacklist", "historical_suspicious_reports")) {
            if (!coverage.has(group)) coverage.set(group, missingCoverage(group));
            else if (coverage.get(group).get("complete").asBoolean()) {
                ObjectNode declared = (ObjectNode)coverage.get(group);
                declared.put("complete", false);
                declared.put("note", declared.get("note").asText() + "; payload contains no complete evidence for this category");
                issues.add(new Issue("COVERAGE_NOT_SUPPORTED_BY_PAYLOAD", "/coverage/" + group, "Declared complete coverage downgraded because category data is absent"));
            }
            issues.add(new Issue("SOURCE_NOT_COVERED_BY_CORE_TABLES", "/coverage/" + group, "Seven core tables do not supply complete evidence for this category"));
        }
        if (ctx.extractedAt() == null || ctx.dictionaryVersion() == null || ctx.queryVersion() == null)
            issues.add(new Issue("SOURCE_METADATA_INCOMPLETE", "/metadata", "Source time or version missing; no values fabricated"));
        if (role.equals("beneficiary")) issues.add(new Issue("BENEFICIARY_RELATIONSHIP_UNKNOWN", "/context/role", "BNFFLAG cannot establish beneficiary identity"));
        return new Adaptation(request, issues, interpretations, provenance);
    }

    private ObjectNode normalizeRow(ObjectNode original, JsonNode definition, String path, List<Issue> issues) {
        if (original.size() > 100000) throw error("PAYLOAD_TOO_LARGE", path, "Core row field count exceeds limit");
        Map<String, JsonNode> sorted = new TreeMap<>();
        original.fields().forEachRemaining(entry -> {
            String field = entry.getKey().toUpperCase(Locale.ROOT);
            if (sorted.containsKey(field)) throw error("INVALID_REQUEST", path, "Duplicate case-insensitive field");
            JsonNode value = entry.getValue();
            if (!value.isValueNode()) throw error("INVALID_REQUEST", path + "/" + pointer(entry.getKey()), "Core field must be scalar");
            JsonNode fieldDefinition = definition.get("fields").get(field);
            if (fieldDefinition == null) issues.add(new Issue("UNKNOWN_CORE_FIELD", path + "/" + pointer(entry.getKey()), "Unknown field retained without interpretation"));
            else if (!value.isNull()) {
                String type = fieldDefinition.get("type").asText().toUpperCase(Locale.ROOT);
                if (type.startsWith("NUMBER") || type.equals("FLOAT") || type.equals("INTEGER")) {
                    if (!value.isNumber() && !value.isTextual()) throw error("INVALID_REQUEST", path + "/" + field, "Numeric field requires decimal value");
                    try {
                        if (value.asText().length() > 80) throw new NumberFormatException();
                        BigDecimal decimal = new BigDecimal(value.asText());
                        if (decimal.precision() > 40 || Math.abs((long)decimal.scale()) > 40) throw new NumberFormatException();
                        value = type.equals("INTEGER") ? BigIntegerNode.valueOf(decimal.toBigIntegerExact()) : TextNode.valueOf(decimal.stripTrailingZeros().toPlainString());
                    } catch (ArithmeticException | NumberFormatException e) { throw error("INVALID_REQUEST", path + "/" + field, "Invalid or oversized decimal value"); }
                } else if (!value.isTextual()) throw error("INVALID_REQUEST", path + "/" + field, "Text/date/code fields require strings to preserve identifiers");
            }
            sorted.put(field, value.deepCopy());
        });
        ObjectNode result = mapper.createObjectNode(); sorted.forEach(result::set); return result;
    }

    private void interpret(String table, String fact, ObjectNode values, String path, List<Issue> issues, List<Interpretation> out) {
        for (String field : List.of("PAYMODE", "NEWPAYMODE", "GETMODE", "CURRENCY", "INSUYEARFLAG", "OCCUPATIONTYPE")) {
            JsonNode node = values.get(field); if (node == null || node.isNull()) continue;
            String raw = node.asText(); String label = null;
            if ((table.equals("LCCONT") || table.equals("LCPOL")) && field.equals("PAYMODE"))
                label = paymentLabels.has(raw) ? paymentLabels.get(raw).asText() : null;
            out.add(new Interpretation(fact, field, raw, label, label == null ? "codebook_required" : "dictionary_label_only"));
            if (label == null || raw.equals("1") || raw.equals("2"))
                issues.add(new Issue(label == null ? "CODEBOOK_REQUIRED" : "CASH_PAYMENT_UNCONFIRMED", path + "/" + field,
                    label == null ? "Keep raw code; no approved field codebook mapping applied" : "Recorded payment mode is not proof of an actual payment"));
        }
    }

    private ObjectNode normalizeCoverage(JsonNode supplied) {
        ObjectNode result = mapper.createObjectNode();
        if (supplied != null && !supplied.isNull()) {
            ObjectNode coverage = object(supplied, "/coverage");
            coverage.fields().forEachRemaining(entry -> {
                String name = entry.getKey(); String path = "/coverage/" + pointer(name);
                ObjectNode raw = object(entry.getValue(), path);
                requireKeys(raw, Set.of("query_status", "coverage_kind", "from", "to", "as_of", "complete", "note"), path);
                String status = text(raw.get("query_status"), path + "/query_status");
                if (!STATUSES.contains(status)) throw error("INVALID_REQUEST", path, "Invalid query status");
                JsonNode complete = raw.get("complete");
                if (complete == null || !complete.isBoolean() || (!status.equals("succeeded") && complete.booleanValue()))
                    throw error("INVALID_REQUEST", path + "/complete", "Invalid coverage completeness");
                String kind = raw.has("coverage_kind") ? text(raw.get("coverage_kind"), path + "/coverage_kind") : coverageKind(name);
                if (!Set.of("snapshot", "history_events", "risk_information").contains(kind)) throw error("INVALID_REQUEST", path, "Invalid coverage kind");
                ObjectNode normalized = result.putObject(name); normalized.put("query_status", status).put("coverage_kind", kind).put("complete", complete.booleanValue());
                for (String field : List.of("from", "to", "as_of")) {
                    JsonNode value = raw.get(field);
                    if (value == null || value.isNull()) normalized.putNull(field);
                    else { String stamp = text(value, path + "/" + field); time(stamp, path + "/" + field); normalized.put(field, stamp); }
                }
                if (!normalized.get("from").isNull() && !normalized.get("to").isNull() &&
                        OffsetDateTime.parse(normalized.get("from").asText()).isAfter(OffsetDateTime.parse(normalized.get("to").asText())))
                    throw error("INVALID_REQUEST", path, "Reversed coverage interval");
                normalized.put("note", raw.has("note") ? text(raw.get("note"), path + "/note") : "Source supplied no coverage note");
            });
        }
        if (result.isEmpty()) result.set("supplied_tables", missingCoverage("supplied_tables"));
        return result;
    }
    private ObjectNode missingCoverage(String name) {
        return mapper.createObjectNode().put("query_status", "not_provided").put("coverage_kind", coverageKind(name))
                .putNull("from").putNull("to").putNull("as_of").put("complete", false).put("note", "Not supplied by this core JSON input");
    }
    private static String coverageKind(String name) {
        if (Set.of("actual_payments", "preservation_events", "individual_claims").contains(name)) return "history_events";
        if (Set.of("supplied_tables", "customer_master", "beneficiary_relationships").contains(name)) return "snapshot";
        return "risk_information";
    }
    private static String normalizeTable(String name) {
        String upper = name.toUpperCase(Locale.ROOT);
        return upper.startsWith("SLISDATA.") ? upper.substring(9) : upper;
    }
    private static ObjectNode object(JsonNode node, String path) {
        if (node == null || !node.isObject()) throw error("INVALID_REQUEST", path, "JSON object required");
        return (ObjectNode)node;
    }
    private static void requireKeys(ObjectNode object, Set<String> allowed, String path) {
        object.fieldNames().forEachRemaining(k -> { if (!allowed.contains(k)) throw error("INVALID_REQUEST", path, "Unsupported input field"); });
    }
    private static String text(JsonNode node, String path) {
        if (node == null || !node.isTextual()) throw error("INVALID_REQUEST", path, "Text value required");
        return node.textValue();
    }
    private static void id(String value, String path) {
        if (value == null || value.isEmpty() || value.length() > 128 || value.codePoints().anyMatch(Character::isWhitespace))
            throw error("INVALID_REQUEST", path, "Identifier required, max 128 characters without whitespace");
    }
    private static void nonempty(String value, String path) {
        if (value == null || value.isBlank()) throw error("INVALID_REQUEST", path, "Nonblank text required");
    }
    private static void time(String value, String path) {
        try { if (!value.matches(".*(Z|[+-][0-9]{2}:[0-9]{2})$")) throw new IllegalArgumentException(); OffsetDateTime.parse(value); }
        catch (RuntimeException ex) { throw error("INVALID_REQUEST", path, "RFC3339 timestamp with offset required"); }
    }
    private static String pointer(String s) { return s.replace("~", "~0").replace("/", "~1"); }
    private static String digest(String s) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
    private static InputException error(String code, String path, String message) { return new InputException(code, path, message); }
}
