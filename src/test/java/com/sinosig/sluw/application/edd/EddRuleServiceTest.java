package com.sinosig.sluw.application.edd;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.sinosig.sluw.application.edd.service.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;

class EddRuleServiceTest {
    private final ObjectMapper mapper=new ObjectMapper();
    private final EddFactService facts=new EddFactService();
    private final EddHistoryService history=new EddHistoryService();
    private JsonNode cases(String name) throws Exception {
        return mapper.readTree(Files.readString(Path.of("tools/edd/fixtures/"+name))).path("cases");
    }
    private ObjectNode input() throws Exception {
        return (ObjectNode)cases("history-cases.json").get(0).path("input").deepCopy();
    }
    private ObjectNode pack() throws Exception {
        return (ObjectNode)mapper.readTree("""
            {"id":"TEST-ONLY","version":"1","approval":"approved","approval_ref":"SYN-APPROVAL",
             "approved_by":"SYN-REVIEWER","approved_at":"2026-01-01T00:00:00+08:00",
             "valid_from":"2026-01-01T00:00:00+08:00","valid_to":"2027-01-01T00:00:00+08:00",
             "test_only":true,"scope":{"subject_type":"natural_person","triggers":["risk_review"]},
             "rules":[{"id":"SYN-GRADE","target":"grade","value":"high","clause_ref":"synthetic://tests/grade",
               "conditions":[{"fact":"cash_status","operator":"eq","expected":"found"}]}]}
            """);
    }
    private ObjectNode evaluate(ObjectNode request,ObjectNode p,EddRuleService.Mode mode) {
        ((ObjectNode)request.path("rule_context")).put("rule_package_id","TEST-ONLY").put("expected_version","1");
        return new EddRuleService(List.of(EddRulePackage.load(p.toString())),mode)
                .evaluate(history.analyze(facts.prepare(request))).result();
    }
    @TestFactory Stream<DynamicTest> allThirtyTwoScenariosLackFormalGradeMapping() throws Exception {
        Map<String,ObjectNode> all=new TreeMap<>();
        for(String file:List.of("history-cases.json","fact-scope-cases.json","rule-extra-cases.json"))
            for(JsonNode c:cases(file))all.putIfAbsent(c.path("case_id").asText(),(ObjectNode)c.path("input").deepCopy());
        ObjectNode longCase=input();ObjectNode template=(ObjectNode)longCase.path("events").get(0).deepCopy();
        ArrayNode events=longCase.putArray("events");
        for(int i=0;i<10000;i++) {
            String id=String.format(Locale.ROOT,"LONG-%05d",i);
            events.add(template.deepCopy().put("fact_id",id).put("source_record_id",id).put("amount","10.00")
                    .put("payment_method",i==8765?"cash":"bank_transfer"));
        }
        all.put("SYN-V1-018",longCase);
        List<DynamicTest> tests=new ArrayList<>();
        for(var e:all.entrySet())tests.add(DynamicTest.dynamicTest(e.getKey(),()->assertNoFormalMapping(e.getKey(),facts.prepare(e.getValue()))));
        for(JsonNode c:cases("core-db-cases.json"))tests.add(DynamicTest.dynamicTest(c.path("case_id").asText(),()->{
            var a=new EddInputAdapter().adaptCoreTables(c.path("input").toString(),
                    new EddInputAdapter.Context(c.path("case_id").asText(),true,"risk_review","CORE",null,null,null));
            assertNoFormalMapping(c.path("case_id").asText(),facts.prepare(a));
        }));
        assertEquals(32,tests.size());return tests.stream();
    }
    private void assertNoFormalMapping(String id,EddFactService.Prepared p) throws Exception {
        var out=new EddRuleService().evaluate(history.analyze(p)).result();
        assertTrue(out.path("proposed_grade").isNull());
        assertEquals("insufficient_rules",out.at("/grade_recommendation/status").asText());
        assertTrue(out.at("/report_recommendation/value").isNull());
        assertFalse(out.path("execution_permitted").asBoolean());
        ObjectNode exported=mapper.createObjectNode();
        exported.set("grade",out.get("grade_recommendation"));exported.set("report",out.get("report_recommendation"));
        Path dir=Path.of("target/edd-rule-output");Files.createDirectories(dir);
        Files.writeString(dir.resolve(id+".json"),mapper.writeValueAsString(exported));
    }
    @Test void approvedTestPackageWorksOnlyInExplicitSyntheticTestMode() throws Exception {
        var out=evaluate(input(),pack(),EddRuleService.Mode.PRODUCTION);
        assertEquals("TEST_PACKAGE_FORBIDDEN",out.at("/rule_package/status").asText());assertTrue(out.path("proposed_grade").isNull());
        out=evaluate(input(),pack(),EddRuleService.Mode.TEST);
        assertEquals("high",out.path("proposed_grade").asText());assertTrue(out.path("test_only").asBoolean());
        assertEquals("TEST-ONLY@1:SYN-GRADE",out.at("/grade_recommendation/rule_refs/0").asText());
        ObjectNode real=input().put("synthetic",false);
        out=evaluate(real,pack(),EddRuleService.Mode.TEST);
        assertTrue(out.path("proposed_grade").isNull());
    }
    @Test void inactiveUnapprovedAndOutOfScopePackagesCannotSuggest() throws Exception {
        for(String state:List.of("draft","rejected")) {
            var out=evaluate(input(),pack().put("approval",state),EddRuleService.Mode.TEST);
            assertEquals("RULE_PACKAGE_NOT_APPROVED",out.at("/rule_package/status").asText());
        }
        var out=evaluate(input(),pack().put("valid_to","2026-09-18T09:00:00+08:00"),EddRuleService.Mode.TEST);
        assertEquals("RULE_PACKAGE_NOT_EFFECTIVE",out.at("/rule_package/status").asText());
        out=evaluate(input(),pack().put("valid_from","2026-10-01T00:00:00+08:00"),EddRuleService.Mode.TEST);
        assertEquals("RULE_PACKAGE_NOT_EFFECTIVE",out.at("/rule_package/status").asText());
        out=evaluate(input(),pack().put("approved_at","2026-10-01T00:00:00+08:00"),EddRuleService.Mode.TEST);
        assertEquals("RULE_APPROVED_AFTER_ANALYSIS_TIME",out.at("/rule_package/status").asText());
        ObjectNode p=pack();((ObjectNode)p.path("scope")).putArray("triggers").add("other");
        out=evaluate(input(),p,EddRuleService.Mode.TEST);
        assertEquals("RULE_SCOPE_MISMATCH",out.at("/rule_package/status").asText());
    }
    @Test void versionsArePinnedAndNeverFallBackToLatest() throws Exception {
        ObjectNode n=input();((ObjectNode)n.path("rule_context")).put("rule_package_id","TEST-ONLY").put("expected_version","2");
        var service=new EddRuleService(List.of(EddRulePackage.load(pack().toString())),EddRuleService.Mode.TEST);
        assertEquals("RULE_PACKAGE_VERSION_NOT_FOUND",service.evaluate(history.analyze(facts.prepare(n))).result().at("/rule_package/status").asText());
        ((ObjectNode)n.path("rule_context")).putNull("expected_version");
        assertEquals("RULE_VERSION_REQUIRED",service.evaluate(history.analyze(facts.prepare(n))).result().at("/rule_package/status").asText());
    }
    @Test void missingDependenciesAndConflictingOutcomesBlockGrade() throws Exception {
        ObjectNode p=pack();
        ((ObjectNode)p.at("/rules/0/conditions/0")).put("fact","current_grade").put("expected","high");
        var out=evaluate(input(),p,EddRuleService.Mode.TEST);
        assertEquals("insufficient_evidence",out.at("/grade_recommendation/status").asText());
        assertTrue(out.path("missing_inputs").toString().contains("current_grade"));
        p=pack();ObjectNode second=((ObjectNode)p.path("rules").get(0)).deepCopy().put("id","CONFLICT").put("value","low");
        ((ArrayNode)p.path("rules")).add(second);
        out=evaluate(input(),p,EddRuleService.Mode.TEST);
        assertEquals("requires_institution_review",out.at("/grade_recommendation/status").asText());
        assertTrue(out.path("proposed_grade").isNull());
    }
    @Test void unknownCashCannotBeTreatedAsRuleNotHit() throws Exception {
        ObjectNode n=input();((ObjectNode)n.path("events").get(0)).put("payment_method","unknown");
        var out=evaluate(n,pack(),EddRuleService.Mode.TEST);
        assertEquals("unknown",out.at("/rule_evaluations/0/status").asText());
        assertEquals("insufficient_evidence",out.at("/grade_recommendation/status").asText());
    }
    private ObjectNode risk(ObjectNode n,String id,String type) {
        ObjectNode r=((ArrayNode)n.path("risk_records")).addObject().put("fact_id",id).put("type",type)
                .put("event_time","2026-01-01T00:00:00+08:00").put("subject_match","confirmed");
        r.set("source",n.path("events").get(0).path("source").deepCopy());return r.putObject("facts");
    }
    @Test void currentHistoricalAndSuggestedGradesRemainSeparate() throws Exception {
        ObjectNode n=input();
        risk(n,"OLD","risk_grade").put("grade","low").put("current",false);
        risk(n,"NOW","risk_grade").put("grade","medium").put("current",true);
        var out=evaluate(n,pack(),EddRuleService.Mode.TEST);
        assertEquals("medium",out.at("/current_grade/value").asText());
        assertEquals("high",out.path("proposed_grade").asText());assertEquals(2,out.path("grade_history").size());
    }
    @Test void confirmedChoiceResolvesOnlyCurrentGradeConflict() throws Exception {
        ObjectNode n=input();risk(n,"A","risk_grade").put("grade","high").put("current",true);
        risk(n,"B","risk_grade").put("grade","low").put("current",true);
        ObjectNode p=pack();((ObjectNode)p.at("/rules/0/conditions/0")).put("fact","current_grade").put("expected","high");
        ObjectNode conflict=n.putArray("conflicts").addObject().put("conflict_id","C").put("field","current_grade").put("resolved",false).putNull("adopted_fact_id");
        conflict.putArray("candidate_fact_ids").add("A").add("B");
        var out=evaluate(n,p,EddRuleService.Mode.TEST);assertTrue(out.path("proposed_grade").isNull());
        conflict.put("resolved",true).put("adopted_fact_id","A");
        out=evaluate(n,p,EddRuleService.Mode.TEST);assertEquals("high",out.path("proposed_grade").asText());
        assertEquals(2,out.path("grade_history").size());
    }
    @Test void historicalReportDoesNotAutomaticallyRequireNewReportOrLowerGrade() throws Exception {
        ObjectNode n=input();risk(n,"PAST","suspicious_report").put("reported",true);
        var out=new EddRuleService().evaluate(history.analyze(facts.prepare(n))).result();
        assertEquals("found",out.path("historical_report_status").asText());
        assertTrue(out.at("/report_recommendation/value").isNull());assertTrue(out.path("proposed_grade").isNull());
        ObjectNode p=pack();ObjectNode r=(ObjectNode)p.path("rules").get(0);
        r.put("target","report").put("value","suggest_not_report");
        out=evaluate(n,p,EddRuleService.Mode.TEST);
        assertEquals("suggest_not_report",out.at("/report_recommendation/value").asText());
        assertTrue(out.path("proposed_grade").isNull());
    }
    @Test void malformedPackagesAreRejectedBeforeRegistration() throws Exception {
        ObjectNode p=pack();p.put("script","arbitrary");
        assertThrows(IllegalArgumentException.class,()->EddRulePackage.load(p.toString()));
        ObjectNode noApproval=pack().putNull("approval_ref");
        assertThrows(IllegalArgumentException.class,()->EddRulePackage.load(noApproval.toString()));
        ObjectNode noConditions=pack();((ObjectNode)noConditions.at("/rules/0")).putArray("conditions");
        assertThrows(IllegalArgumentException.class,()->EddRulePackage.load(noConditions.toString()));
        ObjectNode unknownFact=pack();((ObjectNode)unknownFact.at("/rules/0/conditions/0")).put("fact","free_text");
        assertThrows(IllegalArgumentException.class,()->EddRulePackage.load(unknownFact.toString()));
        var loaded=EddRulePackage.load(pack().toString());
        assertThrows(IllegalArgumentException.class,()->new EddRuleService(List.of(loaded,loaded),EddRuleService.Mode.TEST));
        assertThrows(IllegalArgumentException.class,()->EddRulePackage.load(p.toString()+" {}"));
    }
    @Test void nonMatchAndUnknownCompetingRuleAreDifferent() throws Exception {
        ObjectNode n=input();((ObjectNode)n.path("events").get(0)).put("payment_method","bank_transfer");
        var out=evaluate(n,pack(),EddRuleService.Mode.TEST);
        assertEquals("not_hit",out.at("/rule_evaluations/1/status").asText());
        assertEquals("NO_APPLICABLE_MAPPING",out.at("/grade_recommendation/reason").asText());
        ObjectNode p=pack();ObjectNode competing=((ObjectNode)p.path("rules").get(0)).deepCopy().put("id","NEEDS-CURRENT");
        ((ObjectNode)competing.at("/conditions/0")).put("fact","current_grade").put("expected","highest");
        ((ArrayNode)p.path("rules")).add(competing);
        out=evaluate(input(),p,EddRuleService.Mode.TEST);
        assertEquals("insufficient_evidence",out.at("/grade_recommendation/status").asText());
    }
    @Test void aggregateCashConflictBlocksBothFactorAndMapping() throws Exception {
        ObjectNode n=input();ObjectNode c=n.putArray("conflicts").addObject().put("conflict_id","C")
                .put("field","cash_status").put("resolved",false).putNull("adopted_fact_id");
        c.putArray("candidate_fact_ids").add("OLD-CASH").add("LATEST");
        var out=evaluate(n,pack(),EddRuleService.Mode.TEST);
        assertEquals("unknown",out.at("/rule_evaluations/0/status").asText());
        assertTrue(out.path("proposed_grade").isNull());
    }
}
