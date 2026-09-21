package com.sinosig.sluw.application.edd.service;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EddResultValidatorTest {
    private static final ObjectMapper JSON=new ObjectMapper();
    private ObjectNode input() throws Exception {
        for(JsonNode c:JSON.readTree(Files.readString(Path.of("tools/edd/fixtures/history-cases.json"))).path("cases"))
            if(c.path("case_id").asText().equals("SYN-V1-001"))return (ObjectNode)c.path("input").deepCopy();
        throw new IllegalStateException();
    }
    private EddRuleService.Evaluation evaluation(ObjectNode request){return new EddRuleService().evaluate(new EddHistoryService().analyze(new EddFactService().prepare(request)));}
    private EddRuleRetrieval.Result retrieval(){return new EddRuleRetrieval.Result(EddRuleRetrieval.Status.RULES_MISSING,false,List.of(),List.of(),List.of("RULES_MISSING"));}
    private ObjectNode modelOutput(EddRuleService.Evaluation e) {
        var packed=new EddDraftContext().pack(e,retrieval());ObjectNode out=JSON.createObjectNode();ArrayNode sections=out.putArray("overview_sections");
        for(String key:EddDraftService.SECTIONS){var s=sections.addObject().put("section",key).put("text","资料不足，待机构核实。");s.putArray("fact_refs");s.putArray("rule_refs");}
        if(e.history().summary().at("/cash/status").asText().equals("found")) {
            ObjectNode cash=(ObjectNode)sections.get(2);cash.put("text","已确认历史现金支付，作为风险增加因素，不直接决定风险等级。");
            cash.set("fact_refs",packed.data().at("/business_summary/cash/fact_refs"));
        }
        out.putArray("disposition_recommendations");out.putArray("missing_items");return out;
    }
    private ObjectNode draft(EddRuleService.Evaluation e) {
        ObjectNode out=modelOutput(e);var packed=new EddDraftContext().pack(e,retrieval());
        for(JsonNode s:out.path("overview_sections")){ArrayNode refs=JSON.createArrayNode();for(JsonNode r:s.path("fact_refs"))packed.citations().get(r.asText()).forEach(refs::add);((ObjectNode)s).set("fact_refs",refs);}
        for(String key:List.of("grade_recommendation","report_recommendation","proposed_grade","current_risk_grade","missing_items"))out.set(key,packed.fixed().path(key));return out;
    }
    private ObjectNode section(ObjectNode out,int i){return (ObjectNode)out.path("overview_sections").get(i);}
    private void error(EddResultValidator.Report result,String code){assertFalse(result.valid());assertTrue(result.errors().toString().contains(code),result.errors().toString());}
    @Test void boundMetricsRenderExactValueCurrencyAndGrain() throws Exception {
        var e=evaluation(input());var out=draft(e);JsonNode metric=e.history().summary().at("/transaction_metrics/0");
        section(out,0).put("text","业务交易情况：{{metric:0}}。").set("fact_refs",metric.path("fact_refs"));
        var r=new EddResultValidator().validate(out,e);assertTrue(r.valid(),r.errors().toString());
        String rendered=r.draft().at("/overview_sections/0/text").asText();
        assertTrue(rendered.contains(metric.path("value").asText()));assertTrue(rendered.contains("CNY"));assertTrue(rendered.contains(metric.path("name").asText()));
        assertTrue(out.at("/overview_sections/0/text").asText().contains("{{metric:0}}"));
    }
    @Test void incorrectTotalsDatesAndCountsRequireEvidenceBindings() throws Exception {
        var e=evaluation(input());
        for(String text:List.of("累计保费999999元。","交易发生于2026-09-21。","共有999笔交易。","共三份保单。","累计人民币999元。")) {
            var out=draft(e);section(out,0).put("text",text);error(new EddResultValidator().validate(out,e),"UNBOUND_QUANTITATIVE_CLAIM");
        }
    }
    @Test void bindingRequiresCompleteReferencesAndValidIndex() throws Exception {
        var e=evaluation(input());for(String token:List.of("{{metric:0}}","{{metric:99999}}","{{event:invented}}","{{other:0}}")) {
            var out=draft(e);section(out,0).put("text",token);error(new EddResultValidator().validate(out,e),"INVALID_EVIDENCE_BINDING");
        }
    }
    @Test void eventAndCountBindingsUseOriginalConfirmedRecords() throws Exception {
        var e=evaluation(input());var out=draft(e);var s=section(out,0);s.put("text","{{event:OLD-CASH}}；{{count:premium_payment/completed}}");
        ArrayNode refs=s.putArray("fact_refs");for(JsonNode event:input().path("events"))refs.add(event.path("fact_id"));
        var r=new EddResultValidator().validate(out,e);assertTrue(r.valid(),r.errors().toString());
        assertTrue(r.draft().at("/overview_sections/0/text").asText().contains("2011-01-01"));
        assertTrue(r.draft().at("/overview_sections/0/text").asText().contains("条数="));
    }
    @Test void unknownSourcesCannotBecomeNegativeEvenWithDisclaimerElsewhere() throws Exception {
        var request=input();((ObjectNode)request.at("/coverage/blacklist")).put("query_status","failed").put("complete",false);
        var e=evaluation(request);var out=draft(e);section(out,4).put("text","资料不足。未发现黑名单记录。");
        error(new EddResultValidator().validate(out,e),"UNKNOWN_AS_NEGATIVE");
        section(out,4).put("text","未查询黑名单，不能认定无黑名单记录，待核实。");assertTrue(new EddResultValidator().validate(out,e).valid());
    }
    @Test void cashOmissionAndDenialAreRejected() throws Exception {
        var e=evaluation(input());var out=draft(e);section(out,2).put("text","资料不足。");error(new EddResultValidator().validate(out,e),"CASH_SIGNAL_OMITTED");
        section(out,2).put("text","没有现金支付，无风险。");error(new EddResultValidator().validate(out,e),"CASH_STATUS_CONFLICT");
    }
    @Test void missingRulesCannotProduceDefiniteGradeAndCashCannotMapToGrade() throws Exception {
        var e=evaluation(input());var out=draft(e);section(out,1).put("text","建议定为高风险。");error(new EddResultValidator().validate(out,e),"GRADE_WITHOUT_RULE_BASIS");
        section(out,1).put("text","由于现金支付，因此自动上调为高风险。");error(new EddResultValidator().validate(out,e),"CASH_AUTO_GRADE");
        section(out,1).put("text","现金不直接决定高风险，资料不足，无法确定等级。");assertTrue(new EddResultValidator().validate(out,e).valid());
    }
    @Test void mutuallyExclusiveAdviceAndFixedDecisionTamperingFail() throws Exception {
        var e=evaluation(input());var out=draft(e);ArrayNode advice=(ArrayNode)out.path("disposition_recommendations");
        for(String value:List.of("建议上报可疑交易","建议不上报可疑交易")){var n=advice.addObject().put("status","suggestion").put("value",value).put("reason","待机构确认");n.putArray("fact_refs");n.putArray("rule_refs");}
        error(new EddResultValidator().validate(out,e),"CONTRADICTORY_REPORT_ADVICE");
        out=draft(e);out.put("proposed_grade","high");error(new EddResultValidator().validate(out,e),"FIXED_DECISION_CHANGED");
    }
    @Test void fakeReferencesAndExcludedBusinessCannotSupportAssertions() throws Exception {
        var request=input();((ObjectNode)request.path("events").get(0)).put("one_year_product",true);var e=evaluation(request);var out=draft(e);
        section(out,0).put("text","已确认相关业务。").putArray("fact_refs").add("OLD-CASH");error(new EddResultValidator().validate(out,e),"UNCONFIRMED_FACT_ASSERTION");
        section(out,0).putArray("fact_refs").add("invented");error(new EddResultValidator().validate(out,e),"INVALID_FACT_REFERENCE");
    }
    private ChatResponse response(ObjectNode out){return new ChatResponse(List.of(new Generation(new AssistantMessage(out.toString()))));}
    private EddDraftService service(ChatModel model){return new EddDraftService(()->model,new EddDraftService.Settings(150000,64000,4096,Duration.ofSeconds(5),1,1,"test-v1"));}
    @Test void analysisRepairsOnceAndReturnsValidatedDraftWithGaps() throws Exception {
        var e=evaluation(input());var good=modelOutput(e);var bad=good.deepCopy();section(bad,0).put("text","累计保费999元。");
        ChatModel model=mock(ChatModel.class);AtomicInteger calls=new AtomicInteger();
        when(model.call(any(Prompt.class))).thenAnswer(c->{if(calls.getAndIncrement()==0)return response(bad);assertTrue(((Prompt)c.getArgument(0)).getInstructions().get(1).getText().contains("UNBOUND_QUANTITATIVE_CLAIM"));return response(good);});
        try(var service=service(model)){var r=service.analyze(e,retrieval());assertEquals(EddDraftService.Status.COMPLETED_WITH_GAPS,r.status());assertEquals(2,r.metadata().path("attempt_count").asInt());assertFalse(r.metadata().path("requires_validation").asBoolean());assertTrue(r.metadata().path("requires_institution_review").asBoolean());assertFalse(r.metadata().path("execution_permitted").asBoolean());}
    }
    @Test void formatRepairUsesUpTheSameQuota() throws Exception {
        var e=evaluation(input());var bad=modelOutput(e);section(bad,0).put("text","累计保费999元。");var model=mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("{bad")))),response(bad));
        try(var service=service(model)){var r=service.analyze(e,retrieval());assertEquals(EddDraftService.Status.VALIDATION_FAILED,r.status());assertNull(r.draft());assertFalse(r.facts().isEmpty());verify(model,times(2)).call(any(Prompt.class));}
    }
    @Test void repeatedBusinessFailureDoesNotDeliverBadDraft() throws Exception {
        var e=evaluation(input());var bad=modelOutput(e);section(bad,1).put("text","建议定为高风险。");var model=mock(ChatModel.class);when(model.call(any(Prompt.class))).thenReturn(response(bad));
        try(var service=service(model)){var r=service.analyze(e,retrieval());assertEquals(EddDraftService.Status.VALIDATION_FAILED,r.status());assertNull(r.draft());assertFalse(r.metadata().path("validation_errors").isEmpty());verify(model,times(2)).call(any(Prompt.class));}
    }

    @Test void conflictBlocksBoundValuesButAllowsExplicitReview() throws Exception {
        var base=evaluation(input());var p=base.history().prepared();
        var conflicting=new EddFactService.Prepared(p.input(),p.decisions(),List.of(new EddFactService.FieldChoice("C1","amount",List.of("OLD-CASH"),null,true)),p.issues());
        var e=new EddRuleService.Evaluation(new EddHistoryService.History(conflicting,base.history().summary()),base.result());
        var out=draft(e);section(out,0).put("text","{{event:OLD-CASH}}").putArray("fact_refs").add("OLD-CASH");
        error(new EddResultValidator().validate(out,e),"INVALID_EVIDENCE_BINDING");
        section(out,0).put("text","金额冲突待机构核实。");section(out,2).put("text","现金风险相关金额待核实。");
        var r=new EddResultValidator().validate(out,e);assertTrue(r.valid(),r.errors().toString());assertTrue(r.hasGaps());assertFalse(r.reviews().isEmpty());
    }
    @Test void noGapReportAndReviewOnlyReportAreDistinct() throws Exception {
        var e=evaluation(input());var out=draft(e);out.putArray("missing_items");
        assertFalse(new EddResultValidator().validate(out,e).hasGaps());
        section(out,0).put("text","客户收入稳定。");var r=new EddResultValidator().validate(out,e);assertTrue(r.valid());assertTrue(r.hasGaps());
        assertTrue(r.reviews().toString().contains("TEXT_REQUIRES_EVIDENCE_REVIEW"));
    }
    @Test void validationRepairAlsoSharesDeadline() throws Exception {
        var e=evaluation(input());var good=modelOutput(e);var bad=good.deepCopy();section(bad,0).put("text","累计999元。");
        var model=mock(ChatModel.class);AtomicInteger calls=new AtomicInteger();
        when(model.call(any(Prompt.class))).thenAnswer(c->{int i=calls.getAndIncrement();Thread.sleep(600);return response(i==0?bad:good);});
        try(var service=new EddDraftService(()->model,new EddDraftService.Settings(150000,64000,4096,Duration.ofMillis(1000),1,1,"test-v1"))) {
            var r=service.analyze(e,retrieval());assertEquals(EddDraftService.Status.TIMEOUT,r.status());assertNull(r.draft());assertEquals(2,calls.get());assertFalse(r.facts().isEmpty());
        }
    }
    @Test void oversizedRepairRetainsFailureAndDoesNotMakeAnotherCall() throws Exception {
        var e=evaluation(input());var good=modelOutput(e);var model=mock(ChatModel.class);when(model.call(any(Prompt.class))).thenReturn(response(good));int budget;
        try(var service=service(model)){budget=service.analyze(e,retrieval()).metadata().path("context_bytes").asInt()+1;}
        var bad=good.deepCopy();section(bad,0).put("text","累计999元。");var failing=mock(ChatModel.class);when(failing.call(any(Prompt.class))).thenReturn(response(bad));
        try(var service=new EddDraftService(()->failing,new EddDraftService.Settings(budget,64000,4096,Duration.ofSeconds(5),1,1,"test-v1"))) {
            var r=service.analyze(e,retrieval());assertEquals(EddDraftService.Status.VALIDATION_FAILED,r.status());assertEquals("CONTEXT_LIMIT",r.metadata().path("repair_skipped").asText());verify(failing,times(1)).call(any(Prompt.class));
        }
    }

    @Test void riskAndCoverageDatesAreBoundWithoutInventedValues() throws Exception {
        var request=input();var risk=request.putArray("risk_records").addObject().put("fact_id","R-DATE").put("type","judicial")
                .put("event_time","2023-01-01T00:00:00+08:00").put("subject_match","confirmed");
        risk.set("source",request.path("events").get(0).path("source"));risk.putObject("facts").put("state","frozen");
        var e=evaluation(request);var out=draft(e);section(out,4).put("text","{{risk:R-DATE}}；{{coverage:judicial}}").putArray("fact_refs").add("R-DATE");
        var r=new EddResultValidator().validate(out,e);assertTrue(r.valid(),r.errors().toString());
        assertTrue(r.draft().at("/overview_sections/4/text").asText().contains("2023-01-01"));
        assertTrue(r.draft().at("/overview_sections/4/text").asText().contains("2010-01-01"));
        section(out,4).put("text","{{risk:invented}}；{{coverage:invented}}");error(new EddResultValidator().validate(out,e),"INVALID_EVIDENCE_BINDING");
    }

    @Test void evidenceExpansionCannotProduceUnboundedText() throws Exception {
        var e=evaluation(input());var out=draft(e);section(out,0).put("text","{{metric:0}}".repeat(500)).set("fact_refs",e.history().summary().at("/transaction_metrics/0/fact_refs"));
        error(new EddResultValidator().validate(out,e),"RENDERED_TEXT_TOO_LARGE");
    }

    @Test void commonNegativeFormsCannotHideFailedQueries() throws Exception {
        var request=input();for(String key:List.of("blacklist","historical_suspicious_reports"))((ObjectNode)request.at("/coverage/"+key)).put("query_status","failed").put("complete",false);
        var e=evaluation(request);
        for(String text:List.of("未命中黑名单。","客户不在黑名单内。","黑名单命中：否。","客户未上报可疑交易。")) {
            var out=draft(e);section(out,4).put("text",text);error(new EddResultValidator().validate(out,e),"UNKNOWN_AS_NEGATIVE");
        }
    }
}
