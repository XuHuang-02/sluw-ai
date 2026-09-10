package com.sinosig.sluw.assessment;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Versioned input contract for the independent pilot; facts are mapped explicitly per rule. */
public final class Model {
    private Model() {}
    public enum Verdict { PROBLEM, NON_PROBLEM, UNDETERMINED }
    public enum CheckStatus { PASS, PROBLEM, INCOMPLETE, NOT_APPLICABLE }
    public enum JobStatus { QUEUED, RUNNING, COMPLETED, FAILED }
    public enum Kind { MAX_AMOUNT, REQUIRED_FACT, REQUIRED_DOCUMENT, AI_REVIEW }
    public enum Condition { ALWAYS, AGE_RANGE, IN_SET }
    public enum Role { REVIEWER, ADMIN }

    public record Actor(String username, String org, Role role) {}
    public record Fact(String value, String source, boolean complete, boolean conflict,
            String personId, String amountType, String asOf, Boolean includesCurrent,
            String currency, String basis) {}
    public record Material(String id, String filename, String type, String submissionStatus) {}
    public record Manifest(boolean reliable, String declaredBy, String statement) {}
    public record Input(String caseId, String channel, String product, String insuredId,
            String applicantId, boolean synthetic, Map<String, Fact> facts,
            List<Material> materials, Manifest manifest, String history) {}
    public record Source(String datasetId, String documentId, String chunkId, String text,
            String sha256) {}
    public record Rule(String id, String code, String branch, String title, Kind kind,
            Condition condition, String conditionFact, List<String> values,
            Integer minAge, Integer maxAge, String field, String limit,
            String personRole, String amountType, String currency, String basis,
            Boolean includesCurrent, String advice, Source source) {}
    public record RuleSet(String version, String channel, String product, boolean simulation,
            boolean coverageConfirmed, String confirmedBy, String confirmedAt,
            String scope, List<Rule> rules) {}
    public record Evidence(String reference, String value) {}
    public record Check(String ruleId, String code, String branch, CheckStatus status,
            String reason, String advice, Source source, List<Evidence> evidence) {}
    public record Upload(String id, String originalName, String mediaType, int pages,
            long size, String sha256) {}
    public record Extraction(String uploadId, boolean simulation, String state,
            Map<Integer, String> pages, String note) {}
    public record Review(String id, String by, String at, String decision, String reason) {}
    public record Change(String key, String before, String after) {}
    public record Comparison(String previousId, boolean ruleVersionChanged,
            List<Change> facts, List<Change> materials, List<String> resolved,
            List<String> remaining, List<String> added, List<String> noLongerComparable) {}
    public record Report(Verdict verdict, boolean simulation, RuleSet ruleSet,
            List<Check> checks, List<String> notices, Comparison comparison) {}
    public record Assessment(String id, String org, String createdBy, String createdAt,
            String previousId, String ruleVersion, String switchReason,
            Input input, List<Upload> uploads, JobStatus status, int progress,
            String stage, List<Extraction> extractions, Report report, List<Review> reviews) {}
    public static String now() { return Instant.now().toString(); }
}
