package com.sinosig.sluw.assessment;

import static com.sinosig.sluw.assessment.Model.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

/** Synthetic contracts only: these fixtures do not establish company age or risk-amount policy. */
class RuleEngineTest {
    private final RuleEngine engine = new RuleEngine();
    private static final String CHANNEL = "TEST_CHANNEL";
    private static final String PRODUCT = "TEST_PRODUCT";
    private static final String BASIS = "SYNTHETIC_TOTAL_INCLUDING_CURRENT";

    static class ServicesStub implements RuleEngine.Services {
        final List<String> verified = new ArrayList<>();
        final Set<String> unavailable = new HashSet<>();
        final Set<String> throwing = new HashSet<>();
        boolean simulated;

        @Override public boolean simulation() { return simulated; }
        @Override public boolean verify(Source source) {
            verified.add(source.chunkId());
            if (throwing.contains(source.chunkId())) throw new IllegalStateException("test outage");
            return !unavailable.contains(source.chunkId());
        }
        @Override public Extraction extract(Upload upload) {
            throw new AssertionError("Core evaluation consumes extraction results; it must not run OCR.");
        }
        @Override public String review(Rule rule, Input input, List<Extraction> extractions) {
            return "模型声称应直接判为非问题件";
        }
    }

    @ParameterizedTest(name = "age={0}, amount={1}: {4}")
    @CsvSource({
        "2016-09-11,199999.99,PASS,NOT_APPLICABLE,NON_PROBLEM",
        "2016-09-11,200000.00,PASS,NOT_APPLICABLE,NON_PROBLEM",
        "2016-09-11,200000.01,PROBLEM,NOT_APPLICABLE,PROBLEM",
        "2016-09-10,200000.01,NOT_APPLICABLE,PASS,NON_PROBLEM",
        "2016-09-10,499999.99,NOT_APPLICABLE,PASS,NON_PROBLEM",
        "2016-09-10,500000.00,NOT_APPLICABLE,PASS,NON_PROBLEM",
        "2016-09-10,500000.01,NOT_APPLICABLE,PROBLEM,PROBLEM",
        "2016-09-09,500000.01,NOT_APPLICABLE,PROBLEM,PROBLEM",
        "2008-09-11,500000.01,NOT_APPLICABLE,PROBLEM,PROBLEM",
        "2008-09-10,500000.01,NOT_APPLICABLE,NOT_APPLICABLE,NON_PROBLEM",
        "2008-09-09,500000.01,NOT_APPLICABLE,NOT_APPLICABLE,NON_PROBLEM"
    })
    void ageAndAmountBoundariesKeepBothSameCodeBranches(String birth, String value,
            CheckStatus younger, CheckStatus older, Verdict verdict) {
        ServicesStub services = new ServicesStub();
        List<Integer> progress = new ArrayList<>();
        Report report = engine.evaluate(input(facts(birth, value)), ageRules(true),
                List.of(), List.of(), services, progress::add);

        assertEquals(verdict, report.verdict());
        assertEquals(List.of(younger, older), statuses(report));
        assertEquals(List.of("YBCR0045", "YBCR0045"), report.checks().stream().map(Check::code).toList());
        assertEquals(List.of("under10", "10to18"), report.checks().stream().map(Check::branch).toList());
        assertEquals(List.of("young", "older"), report.checks().stream().map(Check::ruleId).toList());
        assertEquals(List.of("chunk-young", "chunk-older"), services.verified);
        assertEquals(List.of(1, 2), progress);
        assertEquals(ageRules(true), report.ruleSet());
        assertTrue(report.simulation());
        assertTrue(report.checks().stream().allMatch(c -> c.source() != null && !c.evidence().isEmpty()));
    }

    @ParameterizedTest
    @CsvSource({"2999999.99,PASS", "3000000,PASS", "3000000.00,PASS", "3000000.01,PROBLEM"})
    void specialOccupationLimitUsesExactDecimalComparison(String value, CheckStatus expected) {
        Map<String, Fact> facts = facts("1990-01-01", value);
        facts.put("occupation", text("DRIVER"));
        Rule rule = specialRule();
        Report report = evaluate(input(facts), ruleSet(true, rule));
        assertEquals(expected, report.checks().get(0).status());
        assertEquals(expected == CheckStatus.PROBLEM ? Verdict.PROBLEM : Verdict.NON_PROBLEM, report.verdict());
    }

    @Test void nonMatchingOccupationDoesNotRequireAmount() {
        Report report = evaluate(input(Map.of("occupation", text("OTHER"))), ruleSet(true, specialRule()));
        assertEquals(List.of(CheckStatus.NOT_APPLICABLE), statuses(report));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "incomplete", "conflict", "blank", "noSource", "invalidNumber", "negative"})
    void unusableAmountDoesNotBlockAnIndependentCheck(String scenario) {
        Map<String, Fact> facts = facts("2018-01-01", "200000");
        switch (scenario) {
            case "missing" -> facts.remove("risk");
            case "incomplete" -> facts.put("risk", amount("200000", false, false));
            case "conflict" -> facts.put("risk", amount("200000", true, true));
            case "blank" -> facts.put("risk", amount(" ", true, false));
            case "noSource" -> facts.put("risk", new Fact("200000", "", true, false,
                    "insured-1", "TEST_RISK", "2026-09-10", true, "CNY", BASIS));
            case "invalidNumber" -> facts.put("risk", amount("not-a-number", true, false));
            case "negative" -> facts.put("risk", amount("-1", true, false));
            default -> throw new AssertionError(scenario);
        }
        facts.put("independent", text("available"));
        List<Integer> progress = new ArrayList<>();
        RuleSet rules = ruleSet(true, ageRule("young", "under10", 0, 10, "200000"), requiredFact());
        Report report = engine.evaluate(input(facts), rules, List.of(), List.of(), new ServicesStub(), progress::add);
        assertEquals(Verdict.UNDETERMINED, report.verdict());
        assertEquals(List.of(CheckStatus.INCOMPLETE, CheckStatus.PASS), statuses(report));
        assertEquals(List.of(1, 2), progress);
    }

    @Test void knownProblemWinsOverMissingOtherCheckInEitherOrder() {
        Map<String, Fact> facts = facts("2018-01-01", "200000.01");
        Rule limit = ageRule("young", "under10", 0, 10, "200000");
        for (List<Rule> order : List.of(List.of(limit, requiredFact()), List.of(requiredFact(), limit))) {
            Report report = evaluate(input(facts), ruleSet(true, order.toArray(Rule[]::new)));
            assertEquals(Verdict.PROBLEM, report.verdict());
            assertTrue(statuses(report).contains(CheckStatus.INCOMPLETE));
            assertTrue(statuses(report).contains(CheckStatus.PROBLEM));
        }
    }

    @Test void missingAmountRetainsAgeEvidenceAndIdentifiesMissingField() {
        Map<String, Fact> facts = facts("2018-01-01", "200000");
        facts.remove("risk");
        Check check = evaluate(input(facts), ageRules(true)).checks().get(0);
        assertEquals(CheckStatus.INCOMPLETE, check.status());
        assertTrue(check.reason().contains("risk"));
        assertEquals(2, check.evidence().size(), "Do not discard already verified applicability evidence.");
    }

    @ParameterizedTest
    @ValueSource(strings = {"insuredBirthDate", "ageDate"})
    void missingAgeInputsCannotTurnAgeBranchesIntoNotApplicable(String missing) {
        Map<String, Fact> facts = facts("2018-01-01", "200000");
        facts.remove(missing);
        Report report = evaluate(input(facts), ageRules(true));
        assertEquals(Verdict.UNDETERMINED, report.verdict());
        assertEquals(List.of(CheckStatus.INCOMPLETE, CheckStatus.INCOMPLETE), statuses(report));
    }

    @ParameterizedTest
    @CsvSource({"2026-09-11,2026-09-10", "invalid,2026-09-10", "2018-02-30,2026-09-10", "2018-01-01,invalid"})
    void invalidDatesCannotYieldNonProblem(String birth, String date) {
        Map<String, Fact> facts = facts(birth, "200000");
        facts.put("ageDate", text(date));
        assertEquals(Verdict.UNDETERMINED, evaluate(input(facts), ageRules(true)).verdict());
    }

    @Test void configuredReferenceDateNotMachineClockDeterminesAge() {
        Map<String, Fact> facts = facts("2016-09-10", "200000.01");
        facts.put("ageDate", text("2026-09-09"));
        assertEquals(Verdict.PROBLEM, evaluate(input(facts), ageRules(true)).verdict());
        facts.put("ageDate", text("2026-09-10"));
        assertEquals(Verdict.NON_PROBLEM, evaluate(input(facts), ageRules(true)).verdict());
    }

    @ParameterizedTest
    @ValueSource(strings = {"person", "type", "currency", "basis", "includesCurrent", "asOf"})
    void inconsistentAmountMetadataIsIncomplete(String field) {
        Map<String, Fact> facts = facts("2018-01-01", "200000");
        facts.put("risk", new Fact("200000", "synthetic/export", true, false,
                field.equals("person") ? "applicant-1" : "insured-1",
                field.equals("type") ? "OTHER" : "TEST_RISK",
                field.equals("asOf") ? "" : "2026-09-10",
                !field.equals("includesCurrent"), field.equals("currency") ? "USD" : "CNY",
                field.equals("basis") ? "OTHER" : BASIS));
        assertEquals(CheckStatus.INCOMPLETE, evaluate(input(facts), ageRules(true)).checks().get(0).status());
    }

    @Test void unconfirmedOrEmptyChecklistCannotYieldNonProblem() {
        Input input = input(facts("2018-01-01", "200000"));
        assertEquals(Verdict.UNDETERMINED, evaluate(input, ageRules(false)).verdict());
        assertEquals(Verdict.UNDETERMINED, evaluate(input, ruleSet(true)).verdict());
    }

    @ParameterizedTest
    @ValueSource(strings = {"channel", "product"})
    void rulesFromAnotherScopeCannotProduceAProblem(String wrongField) {
        Input original = input(facts("2018-01-01", "900000"));
        Input different = new Input(original.caseId(), wrongField.equals("channel") ? "OTHER" : CHANNEL,
                wrongField.equals("product") ? "OTHER" : PRODUCT, original.insuredId(), original.applicantId(),
                true, original.facts(), original.materials(), original.manifest(), original.history());
        ServicesStub services = new ServicesStub();
        Report report = engine.evaluate(different, ageRules(true), List.of(), List.of(), services, n -> {});
        assertEquals(Verdict.UNDETERMINED, report.verdict());
        assertEquals(List.of(CheckStatus.INCOMPLETE, CheckStatus.INCOMPLETE), statuses(report));
        assertTrue(services.verified.isEmpty(), "Do not call services for the wrong product/channel.");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void ruleSourceUnavailableDoesNotBlockTheNextRule(boolean throwsException) {
        ServicesStub services = new ServicesStub();
        (throwsException ? services.throwing : services.unavailable).add("chunk-young");
        Map<String, Fact> facts = facts("2018-01-01", "200000");
        facts.put("independent", text("ok"));
        Report report = engine.evaluate(input(facts), ruleSet(true,
                ageRule("young", "under10", 0, 10, "200000"), requiredFact()),
                List.of(), List.of(), services, n -> {});
        assertEquals(Verdict.UNDETERMINED, report.verdict());
        assertEquals(List.of(CheckStatus.INCOMPLETE, CheckStatus.PASS), statuses(report));
    }

    @ParameterizedTest
    @ValueSource(strings = {"zeroWidth", "reversed", "negativeAge", "negativeLimit", "unknownPerson", "missingKind", "missingCondition", "missingBasis"})
    void incompleteRuleDefinitionCannotProducePassOrProblem(String defect) {
        Rule normal = ageRule("young", "under10", 0, 10, "200000");
        Rule bad = new Rule(normal.id(), normal.code(), normal.branch(), normal.title(),
                defect.equals("missingKind") ? null : normal.kind(),
                defect.equals("missingCondition") ? null : normal.condition(),
                normal.conditionFact(), normal.values(), defect.equals("negativeAge") ? -1 : 0,
                defect.equals("zeroWidth") ? 0 : defect.equals("reversed") ? -1 : 10,
                normal.field(), defect.equals("negativeLimit") ? "-1" : normal.limit(),
                defect.equals("unknownPerson") ? "UNKNOWN_ROLE" : normal.personRole(),
                normal.amountType(), normal.currency(), defect.equals("missingBasis") ? null : normal.basis(),
                normal.includesCurrent(), normal.advice(), normal.source());
        Report report = evaluate(input(facts("2018-01-01", "200000")), ruleSet(true, bad));
        assertEquals(Verdict.UNDETERMINED, report.verdict());
        assertEquals(CheckStatus.INCOMPLETE, report.checks().get(0).status());
    }

    @Test void duplicateBranchIdentityCannotEstablishCompleteCoverage() {
        Rule young = ageRule("same-id", "under10", 0, 10, "200000");
        Rule older = ageRule("same-id", "10to18", 10, 18, "500000");
        Report report = evaluate(input(facts("2018-01-01", "200000")), ruleSet(true, young, older));
        assertEquals(Verdict.UNDETERMINED, report.verdict());
    }

    @Test void syntheticInputRemainsMarkedEvenWithNonSimulationRuleSet() {
        RuleSet sample = ageRules(true);
        RuleSet formal = new RuleSet(sample.version(), CHANNEL, PRODUCT, false, true,
                sample.confirmedBy(), sample.confirmedAt(), sample.scope(), sample.rules());
        Report report = evaluate(input(facts("2018-01-01", "200000")), formal);
        assertTrue(report.simulation());
        assertFalse(report.notices().isEmpty());
    }

    @ParameterizedTest
    @CsvSource({
        "false,false,false,false,false",
        "true,false,false,false,true",
        "false,true,false,false,true",
        "false,false,true,false,true",
        "false,false,false,true,true"
    })
    void simulationMarkerTracksEachInputIndependently(boolean synthetic, boolean simulatedRules,
            boolean simulatedService, boolean simulatedExtraction, boolean expected) {
        Input original = input(facts("2018-01-01", "200000"));
        Input input = new Input(original.caseId(), CHANNEL, PRODUCT, original.insuredId(), original.applicantId(),
                synthetic, original.facts(), original.materials(), original.manifest(), original.history());
        RuleSet originalSet = ageRules(true);
        RuleSet rules = new RuleSet(originalSet.version(), CHANNEL, PRODUCT, simulatedRules, true,
                originalSet.confirmedBy(), originalSet.confirmedAt(), originalSet.scope(), originalSet.rules());
        ServicesStub services = new ServicesStub();
        services.simulated = simulatedService;
        List<Extraction> extractions = List.of(new Extraction("test-upload", simulatedExtraction,
                "READABLE", Map.of(1, "synthetic text"), "test-only"));
        Report report = engine.evaluate(input, rules, List.of(), extractions, services, n -> {});
        assertEquals(expected, report.simulation());
        assertEquals(expected, report.notices().stream().anyMatch(n -> n.contains("模拟")));
    }

    @Test void sameBusinessBranchCannotBeDuplicatedUnderDifferentIds() {
        Rule first = ageRule("id-one", "under10", 0, 10, "200000");
        Rule duplicate = ageRule("id-two", "under10", 0, 10, "500000");
        Report report = evaluate(input(facts("2018-01-01", "300000")), ruleSet(true, first, duplicate));
        assertEquals(Verdict.UNDETERMINED, report.verdict());
        assertEquals(List.of(CheckStatus.INCOMPLETE, CheckStatus.INCOMPLETE), statuses(report));
    }

    @ParameterizedTest
    @ValueSource(strings = {"version", "confirmedBy", "confirmedAt", "scope"})
    void missingVersionIdentityPreventsExecution(String missing) {
        RuleSet original = ageRules(true);
        RuleSet rules = new RuleSet(missing.equals("version") ? "" : original.version(), CHANNEL, PRODUCT, true, true,
                missing.equals("confirmedBy") ? "" : original.confirmedBy(),
                missing.equals("confirmedAt") ? "" : original.confirmedAt(),
                missing.equals("scope") ? "" : original.scope(), original.rules());
        ServicesStub services = new ServicesStub();
        Report report = engine.evaluate(input(facts("2018-01-01", "900000")), rules,
                List.of(), List.of(), services, n -> {});
        assertEquals(Verdict.UNDETERMINED, report.verdict());
        assertTrue(services.verified.isEmpty());
    }

    @Test void modelCannotPromoteAnUnfinishedCheckToNonProblem() {
        Rule r = requiredFact();
        Rule ai = new Rule(r.id(), r.code(), r.branch(), r.title(), Kind.AI_REVIEW,
                Condition.ALWAYS, null, List.of(), null, null, null, null, null,
                null, null, null, null, r.advice(), r.source());
        Report report = evaluate(input(Map.of()), ruleSet(true, ai));
        assertEquals(Verdict.UNDETERMINED, report.verdict());
        assertEquals(CheckStatus.INCOMPLETE, report.checks().get(0).status());
    }

    @ParameterizedTest(name = "{0} + {1}, coverage={2} -> {3}")
    @MethodSource("verdictCombinations")
    void conclusionPrecedence(CheckStatus first, CheckStatus second, boolean coverage, Verdict expected) {
        List<Check> checks = List.of(check(first), check(second));
        assertEquals(expected, RuleEngine.aggregate(checks, coverage));
    }

    static Stream<Arguments> verdictCombinations() {
        List<Arguments> cases = new ArrayList<>();
        for (boolean coverage : List.of(false, true)) {
            cases.add(Arguments.of(CheckStatus.PASS, CheckStatus.PASS, coverage, coverage ? Verdict.NON_PROBLEM : Verdict.UNDETERMINED));
            cases.add(Arguments.of(CheckStatus.PASS, CheckStatus.NOT_APPLICABLE, coverage, coverage ? Verdict.NON_PROBLEM : Verdict.UNDETERMINED));
            cases.add(Arguments.of(CheckStatus.NOT_APPLICABLE, CheckStatus.NOT_APPLICABLE, coverage, coverage ? Verdict.NON_PROBLEM : Verdict.UNDETERMINED));
            cases.add(Arguments.of(CheckStatus.PASS, CheckStatus.INCOMPLETE, coverage, Verdict.UNDETERMINED));
            cases.add(Arguments.of(CheckStatus.INCOMPLETE, CheckStatus.PASS, coverage, Verdict.UNDETERMINED));
            cases.add(Arguments.of(CheckStatus.PROBLEM, CheckStatus.INCOMPLETE, coverage, Verdict.PROBLEM));
            cases.add(Arguments.of(CheckStatus.INCOMPLETE, CheckStatus.PROBLEM, coverage, Verdict.PROBLEM));
            cases.add(Arguments.of(CheckStatus.PROBLEM, CheckStatus.PASS, coverage, Verdict.PROBLEM));
        }
        return cases.stream();
    }

    private Report evaluate(Input input, RuleSet rules) {
        return engine.evaluate(input, rules, List.of(), List.of(), new ServicesStub(), n -> {});
    }
    private static List<CheckStatus> statuses(Report report) { return report.checks().stream().map(Check::status).toList(); }
    private static Check check(CheckStatus status) { return new Check("test", "test", "test", status, "test", "test", source("test"), List.of()); }
    private static Fact text(String value) { return new Fact(value, "synthetic/export", true, false, null, null, null, null, null, null); }
    private static Fact amount(String value, boolean complete, boolean conflict) {
        return new Fact(value, "synthetic/export/risk", complete, conflict, "insured-1", "TEST_RISK",
                "2026-09-10", true, "CNY", BASIS);
    }
    private static Map<String, Fact> facts(String birth, String value) {
        Map<String, Fact> facts = new HashMap<>();
        facts.put("insuredBirthDate", text(birth));
        facts.put("ageDate", text("2026-09-10"));
        facts.put("risk", amount(value, true, false));
        return facts;
    }
    private static Input input(Map<String, Fact> facts) {
        return new Input("synthetic-case", CHANNEL, PRODUCT, "insured-1", "applicant-1", true,
                facts, List.of(), new Manifest(true, "test", "synthetic-only"), "synthetic history");
    }
    private static Source source(String id) {
        return new Source("synthetic-dataset", "synthetic-document", "chunk-" + id,
                "Synthetic rule, not a published underwriting rule", "0".repeat(64));
    }
    private static RuleSet ageRules(boolean coverage) {
        return ruleSet(coverage, ageRule("young", "under10", 0, 10, "200000"),
                ageRule("older", "10to18", 10, 18, "500000"));
    }
    private static RuleSet ruleSet(boolean coverage, Rule... rules) {
        return new RuleSet("synthetic-v1", CHANNEL, PRODUCT, true, coverage, "test-owner",
                "2026-09-10T00:00:00Z", "Only the checks explicitly listed in this synthetic fixture", List.of(rules));
    }
    private static Rule ageRule(String id, String branch, int minAge, int maxAge, String limit) {
        return new Rule(id, "YBCR0045", branch, "synthetic age limit", Kind.MAX_AMOUNT,
                Condition.AGE_RANGE, "ageDate", List.of(), minAge, maxAge,
                "risk", limit, "INSURED", "TEST_RISK", "CNY", BASIS, true, "核实保额", source(id));
    }
    private static Rule specialRule() {
        return new Rule("special", "YBCR0043", "special-group", "synthetic occupation limit", Kind.MAX_AMOUNT,
                Condition.IN_SET, "occupation", List.of("DRIVER", "FARMER"), null, null,
                "risk", "3000000", "INSURED", "TEST_RISK", "CNY", BASIS, true, "核实累计保额", source("special"));
    }
    private static Rule requiredFact() {
        return new Rule("independent", "TEST_REQUIRED", "main", "synthetic presence check", Kind.REQUIRED_FACT,
                Condition.ALWAYS, null, List.of(), null, null, "independent", null, null,
                null, null, null, null, "补充字段", source("independent"));
    }
}
