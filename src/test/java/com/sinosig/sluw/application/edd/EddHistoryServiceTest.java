package com.sinosig.sluw.application.edd;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.sinosig.sluw.application.edd.service.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;

class EddHistoryServiceTest {
    private final ObjectMapper mapper=new ObjectMapper();
    private final EddFactService facts=new EddFactService();
    private final EddHistoryService history=new EddHistoryService();
    private JsonNode cases(String file) throws Exception {
        return mapper.readTree(Files.readString(Path.of("tools/edd/fixtures/"+file))).path("cases");
    }
    private ObjectNode input(String id) throws Exception {
        for(JsonNode c:cases("history-cases.json"))if(c.path("case_id").asText().equals(id))return (ObjectNode)c.path("input").deepCopy();
        throw new IllegalArgumentException(id);
    }
    private ObjectNode analyze(ObjectNode n){return history.analyze(facts.prepare(n)).summary();}
    private ObjectNode core(String id) throws Exception {
        for(JsonNode c:cases("core-db-cases.json"))if(c.path("case_id").asText().equals(id)) {
            var a=new EddInputAdapter().adaptCoreTables(c.path("input").toString(),
                    new EddInputAdapter.Context(id,true,"review","SYN-CORE","2026-09-18T09:00:00+08:00",null,null));
            return history.analyze(facts.prepare(a)).summary();
        }
        throw new IllegalArgumentException(id);
    }
    private BigDecimal amount(JsonNode out,String name,String currency) {
        for(JsonNode m:out.path("transaction_metrics"))if(m.path("name").asText().equals(name)&&m.path("currency_code").asText().equals(currency))
            return new BigDecimal(m.path("value").asText());
        throw new AssertionError("Missing metric "+name+" "+currency);
    }
    @TestFactory Stream<DynamicTest> syntheticCashScenarios() throws Exception {
        List<DynamicTest> result=new ArrayList<>();
        for(JsonNode c:cases("history-cases.json"))result.add(DynamicTest.dynamicTest(c.path("case_id").asText(),()->{
            var out=analyze((ObjectNode)c.path("input"));
            assertEquals(c.at("/expected/cash_status").asText(),out.at("/cash/status").asText());
            for(JsonNode id:c.at("/expected/excluded_event_ids"))
                assertFalse(out.at("/cash/fact_refs").toString().contains(id.asText()));
        }));
        return result.stream();
    }
    @TestFactory Stream<DynamicTest> allCoreCasesHaveUnknownActualCash() throws Exception {
        List<DynamicTest> result=new ArrayList<>();
        for(JsonNode c:cases("core-db-cases.json")) {
            String id=c.path("case_id").asText();
            result.add(DynamicTest.dynamicTest(id,()->{
                var out=core(id);assertEquals("unknown",out.at("/cash/status").asText());
                assertTrue(out.path("transaction_metrics").isEmpty());assertTrue(out.path("event_history").isEmpty());
                if(!id.equals("DB-009"))assertFalse(out.path("cash_indicators").isEmpty());
            }));
        }
        return result.stream();
    }
    @Test void longHistoryScansEveryRowAndPreservesDecimalTotal() throws Exception {
        ObjectNode n=input("SYN-V1-003");ObjectNode template=(ObjectNode)n.path("events").get(0).deepCopy();
        ArrayNode es=n.putArray("events");
        for(int i=0;i<10000;i++) {
            String id=String.format(Locale.ROOT,"LONG-%05d",i);
            ObjectNode e=template.deepCopy().put("fact_id",id).put("source_record_id",id).put("amount","10.00");
            e.put("payment_method",i==8765?"cash":"bank_transfer");es.add(e);
        }
        var out=analyze(n);
        assertEquals(List.of("LONG-08765"),mapper.convertValue(out.at("/cash/fact_refs"),List.class));
        assertEquals(new BigDecimal("100000.00"),amount(out,"premium_payment.paid_premium.customer_to_company.completed","CNY"));
        assertEquals(10000,out.path("event_history").size());
    }
    @Test void currenciesKindsAndRolesNeverDoubleCount() throws Exception {
        var out=analyze(input("SYN-V1-009"));
        assertEquals(new BigDecimal("10000.00"),amount(out,"premium_payment.paid_premium.customer_to_company.completed","CNY"));
        assertEquals(new BigDecimal("2000.00"),amount(out,"premium_payment.paid_premium.customer_to_company.completed","USD"));
        assertEquals(2,out.path("transaction_metrics").size());
        assertEquals("500000.00",out.path("source_metrics").get(0).path("value").asText());
        out=analyze(input("SYN-V1-008"));
        assertEquals(new BigDecimal("10000.00"),amount(out,"premium_payment.paid_premium.customer_to_company.completed","CNY"));
        assertEquals(1,out.path("observed_event_policy_count").asInt());
    }
    @Test void cashRefundAndUnpaidAreSeparateGrossFlows() throws Exception {
        var out=analyze(input("SYN-V1-020"));
        assertEquals(List.of("PAID-CASH"),mapper.convertValue(out.at("/cash/fact_refs"),List.class));
        assertEquals(new BigDecimal("11000.00"),amount(out,"premium_payment.paid_premium.customer_to_company.completed","CNY"));
        assertEquals(new BigDecimal("10000.00"),amount(out,"premium_payment.paid_premium.customer_to_company.pending","CNY"));
        assertEquals(new BigDecimal("1000.00"),amount(out,"premium_refund.refund.company_to_customer.completed","CNY"));
    }
    @Test void nonFinancialPreservationAndIndividualClaimsRemainVisible() throws Exception {
        var out=analyze(input("SYN-V1-006"));
        assertEquals(5,out.path("event_history").size());
        assertEquals(1,out.path("event_counts").path("beneficiary_change/completed").asInt());
        assertEquals(new BigDecimal("50000.00"),amount(out,"policy_loan.loan_principal.company_to_customer.completed","CNY"));
        assertEquals(new BigDecimal("1000.00"),amount(out,"loan_repayment.loan_repayment.customer_to_company.completed","CNY"));
        out=analyze(input("SYN-V1-007"));
        assertEquals(new BigDecimal("3000.00"),amount(out,"claim_payment.claim_payment.company_to_customer.completed","CNY"));
    }
    @Test void sourceLayersAndBenefitCountsDoNotBecomePayments() throws Exception {
        var out=core("DB-002");assertEquals(1,out.path("observed_source_policy_count").asInt());
        var policyMetrics=StreamSupport.stream(out.path("source_metrics").spliterator(),false)
                .filter(m->m.path("name").asText().equals("policy.PREM")).toList();
        assertEquals(1,policyMetrics.size());assertEquals("10000",policyMetrics.get(0).path("value").asText());
        assertEquals("01",policyMetrics.get(0).path("currency_code").asText());
        assertTrue(policyMetrics.get(0).path("currency_label").isNull());
        out=core("DB-006");assertTrue(out.path("source_metrics").toString().contains("benefit.ACTUGET"));
        assertTrue(out.path("transaction_metrics").isEmpty());
        out=core("DB-007");assertTrue(out.path("source_metrics").toString().contains("product.ENDORSETIMES"));
        assertEquals(0,out.path("event_history").size());
    }
    @Test void stateIntervalsAndSigningUseEffectiveRatherThanCollectionTime() throws Exception {
        var out=core("DB-003");assertEquals(2,out.path("state_history").size());
        assertEquals("0",out.path("current_states").get(0).path("raw_state").asText());
        assertEquals("unknown",out.path("whole_policy_state").asText());
        out=core("DB-008");assertEquals("SYN-C002",out.at("/latest_signing/policy_id").asText());
        assertEquals(2,out.path("signing_history").size());
    }
    @Test void foundCashSurvivesCoverageGapsAndUnknownIsNotNo() throws Exception {
        ObjectNode n=input("SYN-V1-001");
        ((ObjectNode)n.at("/coverage/underwriting")).put("complete",false);
        var out=analyze(n);assertEquals("found",out.at("/cash/status").asText());
        assertTrue(out.at("/cash/coverage_or_evidence_gaps").asBoolean());
        n=input("SYN-V1-003");((ObjectNode)n.path("events").get(0)).put("status","reversed");
        out=analyze(n);assertEquals("unknown",out.at("/cash/status").asText());
        n=input("SYN-V1-003");((ObjectNode)n.path("events").get(0)).putNull("currency");
        out=analyze(n);assertTrue(out.path("transaction_metrics").isEmpty());
    }
    @Test void fieldConflictHoldsOnlyAffectedMetricAndAdoptedCandidate() throws Exception {
        ObjectNode n=input("SYN-V1-003");ArrayNode es=(ArrayNode)n.path("events");
        ObjectNode other=((ObjectNode)es.get(0)).deepCopy().put("fact_id","ALT").put("source_record_id","ALT").put("amount","20000.00");es.add(other);
        ObjectNode conflict=n.putArray("conflicts").addObject().put("conflict_id","C").put("field","amount").put("resolved",false).putNull("adopted_fact_id");
        conflict.putArray("candidate_fact_ids").add("T1").add("ALT");
        var out=analyze(n);assertTrue(out.path("transaction_metrics").isEmpty());
        conflict.put("resolved",true).put("adopted_fact_id","T1");
        out=analyze(n);assertEquals(new BigDecimal("10000.00"),amount(out,"premium_payment.paid_premium.customer_to_company.completed","CNY"));
    }
    @Test void overlappingCurrentStatesStayUnknownAndRetainBothSources() throws Exception {
        ObjectNode request=null;
        for(JsonNode c:cases("core-db-cases.json"))if(c.path("case_id").asText().equals("DB-003")) {
            var a=new EddInputAdapter().adaptCoreTables(c.path("input").toString(),
                    new EddInputAdapter.Context("state",true,"review","CORE",null,null,null));
            request=a.request();
        }
        assertNotNull(request);
        ArrayNode rows=(ArrayNode)request.path("core_snapshots");
        ObjectNode other=((ObjectNode)rows.get(rows.size()-1)).deepCopy().put("fact_id","STATE-OVERLAP");
        ((ObjectNode)other.path("values")).put("STARTDATE","2026-01-01").put("STATE","1");
        ((ObjectNode)other.at("/source/key")).put("STARTDATE","2026-01-01");
        rows.add(other);
        var out=analyze(request);
        assertEquals("unknown",out.path("current_states").get(0).path("status").asText());
        assertTrue(out.path("current_states").get(0).path("raw_state").isNull());
        assertEquals(3,out.path("state_history").size());
        ((ObjectNode)other.path("values")).put("STARTDATE","2026-09-18");
        ((ObjectNode)other.at("/source/key")).put("STARTDATE","2026-09-18");
        assertEquals("unknown",analyze(request).path("current_states").get(0).path("status").asText());
    }
    @Test void cashAbsenceRequiresTheActualEventIntervalToBeCovered() throws Exception {
        ObjectNode n=input("SYN-V1-003");
        ((ObjectNode)n.at("/coverage/underwriting")).put("from","2026-01-01T00:00:00+08:00");
        assertEquals("unknown",analyze(n).at("/cash/status").asText());
    }
    @Test void zeroAndCancelledCashDoNotProvePaymentAndReturnedSummaryIsIsolated() throws Exception {
        ObjectNode n=input("SYN-V1-003");ObjectNode e=(ObjectNode)n.path("events").get(0);
        e.put("amount","0").put("payment_method","cash");
        assertEquals("not_observed_in_available_scope",analyze(n).at("/cash/status").asText());
        e.put("amount","100").put("status","cancelled");
        var result=history.analyze(facts.prepare(n));
        assertEquals("not_observed_in_available_scope",result.summary().at("/cash/status").asText());
        result.summary().removeAll();
        assertTrue(result.summary().has("cash"));
    }
}
