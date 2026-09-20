package com.sinosig.sluw.application.edd;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.sinosig.sluw.application.edd.service.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.sinosig.sluw.application.edd.service.EddFactService.State.*;

class EddFactServiceTest {
    private final ObjectMapper mapper=new ObjectMapper();
    private final EddFactService service=new EddFactService();
    private JsonNode fixtures() throws Exception {
        return mapper.readTree(Files.readString(Path.of("tools/edd/fixtures/fact-scope-cases.json"))).get("cases");
    }
    private ObjectNode input(String id) throws Exception {
        for(JsonNode c:fixtures()) if(c.path("case_id").asText().equals(id)) return (ObjectNode)c.get("input").deepCopy();
        throw new IllegalArgumentException(id);
    }
    @TestFactory Stream<DynamicTest> scopeScenarios() throws Exception {
        List<DynamicTest> tests=new ArrayList<>();
        for(JsonNode c:fixtures()) tests.add(DynamicTest.dynamicTest(c.path("case_id").asText(),()->{
            ObjectNode request=(ObjectNode)c.get("input");var p=service.prepare(request);
            Map<String,EddFactService.State> groups=Map.of("included_event_ids",INCLUDED,"excluded_event_ids",EXCLUDED,"pending_scope_event_ids",PENDING);
            for(var group:groups.entrySet())for(JsonNode id:c.path("expected").path(group.getKey()))
                assertEquals(group.getValue(),p.decisions().get(id.asText()).state(),id.asText());
            assertEquals(request,p.input().request());
        }));
        return tests.stream();
    }
    @Test void roleDuplicateCountsOnceAndKeepsBothFacts() throws Exception {
        var p=service.prepare(input("SYN-V1-008"));
        assertEquals(Set.of("policyholder","insured"),p.decisions().get("T1").roles());
        assertEquals(DUPLICATE,p.decisions().get("T1-ROLE").state());
        assertEquals("T1",p.decisions().get("T1-ROLE").duplicateOf());
        assertFalse(p.decisions().get("T1-ROLE").amountEligible());
        assertEquals(2,p.input().request().path("events").size());
    }
    @Test void conflictingOrCrossSourceDuplicatesAreHeld() throws Exception {
        ObjectNode n=input("SYN-V1-008");
        ((ObjectNode)n.path("events").get(1)).put("amount","20000");
        var p=service.prepare(n);assertEquals(PENDING,p.decisions().get("T1").state());
        n=input("SYN-V1-008");
        ((ObjectNode)n.path("events").get(1).path("source")).put("source_id","OTHER");
        p=service.prepare(n);assertEquals(PENDING,p.decisions().get("T1").state());
        assertFalse(p.decisions().get("T1").amountEligible());
    }
    @Test void incomeChoiceBlocksOnlyItsFieldAndKeepsHistory() throws Exception {
        var p=service.prepare(input("SYN-V1-014"));
        assertTrue(p.fieldBlocked("family_annual_income"));
        assertFalse(p.fieldBlocked("cash_history"));
        assertEquals(INCLUDED,p.decisions().get("T1").state());
        p=service.prepare(input("SYN-V1-015"));
        assertFalse(p.fieldBlocked("family_annual_income"));
        assertEquals("INCOME-A",p.fieldChoices().get(0).adoptedFactId());
        assertEquals(2,p.input().request().path("manual_excerpts").size());
        assertEquals("SYN-PREVIOUS-ANALYSIS",p.input().request().path("parent_analysis_id").asText());
    }
    @Test void unconfirmedAdoptionAndMissingReferencesAreRejected() throws Exception {
        ObjectNode n=input("SYN-V1-014");
        ((ObjectNode)n.path("conflicts").get(0)).put("resolved",true).put("adopted_fact_id","INCOME-A");
        assertThrows(IllegalArgumentException.class,()->service.prepare(n));
        ObjectNode missing=input("SYN-V1-014");
        ((ArrayNode)missing.path("conflicts").get(0).path("candidate_fact_ids")).set(0,TextNode.valueOf("MISSING"));
        assertThrows(IllegalArgumentException.class,()->service.prepare(missing));
    }
    @Test void externalNameMatchAndUnextractedAttachmentNeverBecomeFacts() throws Exception {
        var p=service.prepare(input("SYN-V1-013"));
        assertEquals(PENDING,p.decisions().get("NAME-MATCH").state());
        p=service.prepare(input("SYN-V1-016"));
        assertTrue(p.issues().stream().anyMatch(i->i.code().equals("ATTACHMENT_NOT_EXTRACTED")));
        assertTrue(p.input().request().path("manual_excerpts").isEmpty());
    }
    @Test void preservesCoverageStatesAndNonMonetaryEvents() throws Exception {
        var p=service.prepare(input("SYN-V1-010"));
        assertEquals("failed",p.input().request().at("/coverage/judicial/query_status").asText());
        assertEquals("permission_denied",p.input().request().at("/coverage/blacklist/query_status").asText());
        assertEquals("not_queried",p.input().request().at("/coverage/historical_suspicious_reports/query_status").asText());
        ObjectNode n=input("SYN-V1-004");ObjectNode e=(ObjectNode)n.path("events").get(0);
        e.put("business_type","address_change").putNull("amount");
        p=service.prepare(n);assertEquals(INCLUDED,p.decisions().get("T1").state());
        assertFalse(p.decisions().get("T1").amountEligible());
    }
    @TestFactory Stream<DynamicTest> coreScopeScenarios() throws Exception {
        JsonNode cases=mapper.readTree(Files.readString(Path.of("tools/edd/fixtures/core-db-cases.json"))).path("cases");
        List<DynamicTest> tests=new ArrayList<>();
        for(JsonNode c:cases) {
            String id=c.path("case_id").asText();
            if(!Set.of("DB-004","DB-005","DB-010").contains(id))continue;
            tests.add(DynamicTest.dynamicTest(id,()->{
                var a=new EddInputAdapter().adaptCoreTables(c.get("input").toString(),
                    new EddInputAdapter.Context(id,true,"risk_review","SYN-CORE",null,"core-structure-v1",null));
                var p=service.prepare(a);
                assertEquals(a.provenance(),p.input().provenance());
                assertEquals(a.interpretations(),p.input().interpretations());
                assertTrue(p.issues().containsAll(a.issues()));
                for(JsonNode s:a.request().path("core_snapshots")) {
                    var d=p.decisions().get(s.path("fact_id").asText());
                    assertFalse(d.amountEligible());
                    if(id.equals("DB-004")&&s.path("snapshot_type").asText().equals("product"))assertEquals(PENDING,d.state());
                    if(id.equals("DB-005"))assertFalse(d.roles().contains("beneficiary"));
                    if(id.equals("DB-010")&&s.path("snapshot_type").asText().equals("product"))
                        assertEquals(s.at("/values/YEARS").asText().equals("1")?EXCLUDED:INCLUDED,d.state());
                }
            }));
        }
        return tests.stream();
    }
    @Test void cutoffAndOtherCustomerDoNotContributeAmounts() throws Exception {
        ObjectNode n=input("SYN-V1-019");var p=service.prepare(n);
        assertFalse(p.decisions().get("FUTURE-CASH").amountEligible());
        ((ObjectNode)n.path("events").get(0)).put("customer_id","OTHER");
        assertEquals(EXCLUDED,service.prepare(n).decisions().get("T1").state());
    }
    @Test void resultCannotMutateOriginalInput() throws Exception {
        ObjectNode n=input("SYN-V1-008");var p=service.prepare(n);
        n.remove("events");
        assertEquals(2,p.input().request().path("events").size());
        assertThrows(UnsupportedOperationException.class,()->p.decisions().clear());
    }
}
