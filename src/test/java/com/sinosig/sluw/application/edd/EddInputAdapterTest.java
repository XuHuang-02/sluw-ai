package com.sinosig.sluw.application.edd;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.sinosig.sluw.application.edd.service.EddInputAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;

class EddInputAdapterTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final EddInputAdapter adapter = new EddInputAdapter();
    private static final Path FIXTURES = Path.of("tools/edd/fixtures/core-db-cases.json");
    private EddInputAdapter.Context metadata(String id) {
        return new EddInputAdapter.Context(id,true,"risk_review","SYN-CORE",null,"core-structure-v1",null);
    }
    private ObjectNode input(int index) throws Exception { return (ObjectNode)mapper.readTree(Files.readString(FIXTURES)).get("cases").get(index).get("input").deepCopy(); }
    private EddInputAdapter.Adaptation adapt(ObjectNode n) { return adapter.adaptCoreTables(n.toString(), metadata("TEST")); }
    private ObjectNode tables(ObjectNode n) { return (ObjectNode)n.get("raw_tables"); }
    private JsonNode values(EddInputAdapter.Adaptation a, String type) {
        return StreamSupport.stream(a.request().get("core_snapshots").spliterator(),false).filter(x -> x.get("snapshot_type").asText().equals(type)).findFirst().orElseThrow().get("values");
    }
    private boolean issue(EddInputAdapter.Adaptation a,String code) { return a.issues().stream().anyMatch(x -> x.code().equals(code)); }

    @TestFactory Stream<DynamicTest> allTenFixturesProduceTraceableSnapshots() throws Exception {
        List<DynamicTest> tests = new ArrayList<>();
        for(JsonNode c: mapper.readTree(Files.readString(FIXTURES)).get("cases")) {
            tests.add(DynamicTest.dynamicTest(c.get("case_id").asText(), () -> {
                var a=adapter.adaptCoreTables(c.get("input").toString(),metadata(c.get("case_id").asText()));
                JsonNode request=a.request();int expected=0;for(JsonNode rows:c.get("input").get("raw_tables"))expected+=rows.size();
                assertEquals(expected,request.get("core_snapshots").size());assertEquals(expected,a.provenance().size());
                assertTrue(request.get("events").isEmpty());assertTrue(request.get("risk_records").isEmpty());
                assertTrue(request.get("rule_context").get("rule_package_id").isNull());
                assertEquals("not_provided",request.at("/coverage/actual_payments/query_status").asText());
                assertNull(request.get("current_risk_grade"));
                Set<String> ids=new HashSet<>();
                for(var row:a.provenance()) { assertTrue(ids.add(row.factId()));assertEquals(c.get("input").at(row.inputPointer()),row.originalRow()); }
                for(JsonNode s:request.get("core_snapshots")) {
                    assertTrue(ids.contains(s.get("fact_id").asText()));
                    assertTrue(s.at("/source/extracted_at").isNull());
                    assertEquals("core-structure-v1",s.at("/source/dictionary_version").asText());
                }
                Path output=Path.of("target/edd-adapter-output");Files.createDirectories(output);
                Files.writeString(output.resolve(c.get("case_id").asText()+".json"),mapper.writerWithDefaultPrettyPrinter().writeValueAsString(request));
            }));
        }
        return tests.stream();
    }
    @Test void cashModeIsOnlyALabelAndUnknownCurrencyStaysUnknown() throws Exception {
        var a=adapt(input(0));assertEquals("10000",values(a,"policy").get("PREM").asText());
        assertTrue(a.interpretations().stream().anyMatch(x -> x.field().equals("PAYMODE") && "现金".equals(x.label())));
        assertTrue(a.interpretations().stream().anyMatch(x -> x.field().equals("CURRENCY") && x.label()==null && x.rawCode().equals("01")));
        assertTrue(issue(a,"CASH_PAYMENT_UNCONFIRMED"));assertFalse(a.request().has("risk_signals"));
    }
    @Test void preservesAllSevenGrainsAndCompositeKeys() throws Exception {
        ObjectNode n=input(1);ObjectNode raw=tables(n);
        raw.set("LCCONTSTATE",input(2).at("/raw_tables/LCCONTSTATE"));
        raw.set("LCGET",input(5).at("/raw_tables/LCGET"));
        raw.set("T_SLIS_LC_EXPANSION",input(8).at("/raw_tables/T_SLIS_LC_EXPANSION"));
        var a=adapt(n);Set<String> kinds=new HashSet<>();
        for(JsonNode s:a.request().get("core_snapshots")){
            kinds.add(s.get("snapshot_type").asText());
            if(s.get("snapshot_type").asText().equals("premium_plan")) assertEquals(3,s.at("/source/key").size());
            if(s.get("snapshot_type").asText().equals("policy_state")) assertEquals(5,s.at("/source/key").size());
        }
        assertEquals(7,kinds.size());assertFalse(issue(a,"TABLE_NOT_PROVIDED"));
    }
    @Test void unknownGetmodeAndNewpaymodeDoNotInheritCashCodes() throws Exception {
        var a=adapt(input(5));assertTrue(a.interpretations().stream().anyMatch(x -> x.field().equals("GETMODE") && x.label()==null));
        a=adapt(input(8));assertTrue(a.interpretations().stream().anyMatch(x -> x.field().equals("NEWPAYMODE") && x.label()==null));
        assertEquals("SYN-AGENT-ID",values(a,"policy_extension").get("AGENTPHONE").asText());
        assertNull(a.request().get("subject").get("phone"));
    }
    @Test void keepsBeneficiaryAndRiskScoreAsUnresolvedSourceData() throws Exception {
        var a=adapt(input(4));assertTrue(issue(a,"BENEFICIARY_RELATIONSHIP_UNKNOWN"));
        a=adapt(input(6));assertEquals("99",values(a,"premium_plan").get("SUPPRISKSCORE").asText());
        assertTrue(a.request().get("risk_records").isEmpty());
    }
    @Test void doesNotCollapseDifferentPeriodsOrProductTerms() throws Exception {
        var a=adapt(input(2));assertEquals(4,a.request().get("core_snapshots").size());
        a=adapt(input(9));assertEquals(3,a.request().get("core_snapshots").size());
        assertEquals("11000",values(a,"policy").get("PREM").asText());assertFalse(a.request().has("business_summary"));
    }
    @Test void normalizesNamesAndNumbersWhilePreservingRawRow() throws Exception {
        ObjectNode n=input(0);ObjectNode raw=tables(n);raw.removeAll();
        ObjectNode row=raw.putArray("slisdata.lcprem").addObject();row.put("PolNo","0001").put("DutyCode","D").put("PayPlanCode","A").put("Prem","1.234567890123456789E2").put("PayTimes","2");
        var a=adapt(n);JsonNode v=values(a,"premium_plan");assertEquals("123.4567890123456789",v.get("PREM").asText());assertEquals(2,v.get("PAYTIMES").asInt());
        assertEquals(row,a.provenance().get(0).originalRow());assertEquals("0001",a.request().at("/core_snapshots/0/source/key/POLNO").asText());
    }
    @Test void duplicateSourceRowsAreNotSilentlyDeduplicated() throws Exception {
        ObjectNode n=input(0);ArrayNode rows=(ArrayNode)tables(n).get("LCCONT");rows.add(rows.get(0).deepCopy());
        var a=adapt(n);assertEquals(3,a.request().get("core_snapshots").size());
        assertNotEquals(a.request().at("/core_snapshots/0/fact_id"),a.request().at("/core_snapshots/1/fact_id"));
    }
    @Test void missingKeysAndUnknownFieldsArePreservedAsGaps() throws Exception {
        ObjectNode n=input(0);ObjectNode row=(ObjectNode)tables(n).get("LCCONT").get(0);row.remove("CONTNO");row.put("NEW_EXTERNAL_CODE","x");
        var a=adapt(n);assertTrue(issue(a,"SOURCE_KEY_INCOMPLETE"));assertTrue(issue(a,"UNKNOWN_CORE_FIELD"));assertEquals("x",values(a,"policy").get("NEW_EXTERNAL_CODE").asText());
    }
    @Test void rejectsUnknownTablesAndCaseCollisions() throws Exception {
        ObjectNode n=input(0);tables(n).putArray("UNKNOWN_TABLE");assertThrows(EddInputAdapter.InputException.class,()->adapt(n));
        ObjectNode collision=input(0);((ObjectNode)tables(collision).get("LCCONT").get(0)).put("contno","shadow");assertThrows(EddInputAdapter.InputException.class,()->adapt(collision));
        ObjectNode aliases=input(0);tables(aliases).putArray("slisdata.lccont");assertThrows(EddInputAdapter.InputException.class,()->adapt(aliases));
    }
    @Test void rejectsDuplicateJsonKeysTrailingTokensAndNonObjects() {
        for(String invalid:List.of("{\"x\":1,\"x\":2}","{} {}","[]","null",""))assertThrows(EddInputAdapter.InputException.class,()->adapter.adaptCoreTables(invalid,metadata("T")));
    }
    @Test void rejectsInvalidDecimalAndNumericIdentifier() throws Exception {
        for(JsonNode bad:List.of(TextNode.valueOf("NaN"),TextNode.valueOf("1e1000000"),BooleanNode.TRUE)) {
            ObjectNode n=input(0);((ObjectNode)tables(n).get("LCCONT").get(0)).set("PREM",bad);assertThrows(EddInputAdapter.InputException.class,()->adapt(n));
        }
        ObjectNode n=input(0);((ObjectNode)tables(n).get("LCCONT").get(0)).put("CONTNO",123);assertThrows(EddInputAdapter.InputException.class,()->adapt(n));
    }
    @Test void rejectsCallerApprovedRulesAndInvalidTime() throws Exception {
        ObjectNode n=input(0);n.put("approved_rating_rules_available",true);assertThrows(EddInputAdapter.InputException.class,()->adapt(n));
        n.put("approved_rating_rules_available",false).put("analysis_as_of","2026-02-30T09:00:00+08:00");assertThrows(EddInputAdapter.InputException.class,()->adapt(n));
    }
    @Test void preservesFailedCoverageAndMetadata() throws Exception {
        ObjectNode n=input(0);ObjectNode c=(ObjectNode)n.at("/coverage/actual_payments");c.put("query_status","failed").put("complete",false);
        var ctx=new EddInputAdapter.Context("S",false,"review","CORE","2026-09-18T00:00:00Z","v1","q2");
        var a=adapter.adaptCoreTables(n.toString(),ctx);assertEquals("failed",a.request().at("/coverage/actual_payments/query_status").asText());
        assertEquals("2026-09-18T00:00:00Z",a.request().at("/core_snapshots/0/source/extracted_at").asText());
        assertEquals("core_json",a.request().at("/core_snapshots/0/source/source_kind").asText());
        c.put("complete",true);assertThrows(EddInputAdapter.InputException.class,()->adapt(n));
    }
    @Test void resultIsDefensivelyCopied() throws Exception {
        var a=adapt(input(0));a.request().removeAll();a.provenance().get(0).originalRow().removeAll();
        assertTrue(a.request().has("subject"));assertFalse(a.provenance().get(0).originalRow().isEmpty());
    }
    @Test void cannotClaimCompleteHistoryFromSnapshots() throws Exception {
        ObjectNode n=input(0);((ObjectNode)n.at("/coverage/actual_payments")).put("query_status","succeeded").put("complete",true);
        var a=adapt(n);assertFalse(a.request().at("/coverage/actual_payments/complete").asBoolean());assertTrue(issue(a,"COVERAGE_NOT_SUPPORTED_BY_PAYLOAD"));
    }
    @Test void knownTransferLabelsArePreservedWithoutInferringCashHistory() throws Exception {
        ObjectNode n=input(0);((ObjectNode)tables(n).get("LCCONT").get(0)).put("PAYMODE","4");
        var a=adapt(n);assertTrue(a.interpretations().stream().anyMatch(x -> x.rawCode().equals("4") && x.label()!=null));assertTrue(a.request().get("events").isEmpty());
    }
}
