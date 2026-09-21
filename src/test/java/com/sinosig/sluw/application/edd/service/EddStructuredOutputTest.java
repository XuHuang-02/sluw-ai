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
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EddStructuredOutputTest {
    private final ObjectMapper json=new ObjectMapper();
    private ObjectNode output() {
        ObjectNode out=json.createObjectNode();var sections=out.putArray("overview_sections");
        for(String name:EddDraftService.SECTIONS) {
            var s=sections.addObject().put("section",name).put("text","资料待核实");s.putArray("fact_refs");s.putArray("rule_refs");
        }
        out.putArray("disposition_recommendations");out.putArray("missing_items");return out;
    }
    private EddRuleService.Evaluation evaluation() throws Exception {
        var request=(ObjectNode)json.readTree(Files.readString(Path.of("docs/edd/examples/request-minimal.json")));
        return new EddRuleService().evaluate(new EddHistoryService().analyze(new EddFactService().prepare(request)));
    }
    private EddRuleRetrieval.Result retrieval(){return new EddRuleRetrieval.Result(EddRuleRetrieval.Status.RULES_MISSING,false,List.of(),List.of(),List.of());}
    private ChatResponse response(String raw){return new ChatResponse(List.of(new Generation(new AssistantMessage(raw))));}
    private EddDraftService service(ChatModel model,int budget,Duration timeout) {
        return new EddDraftService(()->model,new EddDraftService.Settings(budget,64000,4096,timeout,1,1,"synthetic-v2"));
    }
    @Test void converterGeneratesSchemaAndConvertsTypedRecords() throws Exception {
        var converter=new EddDraftOutput.Converter();var schema=json.readTree(converter.getJsonSchema());
        assertTrue(schema.toString().contains("historical_cash"));assertTrue(schema.toString().contains("overview_sections"));
        assertEquals(6,converter.convert(output().toString()).overviewSections().size());
        var missing=output();missing.remove("missing_items");
        assertEquals(EddDraftOutput.ErrorCode.MISSING_FIELD,assertThrows(EddDraftOutput.InvalidOutput.class,()->converter.convert(missing.toString())).code());
    }
    @Test void malformedOutputGetsOneTargetedRepairAndRetainsOriginalFacts() throws Exception {
        ChatModel model=mock(ChatModel.class);var prompts=new ArrayList<Prompt>();
        when(model.call(any(Prompt.class))).thenAnswer(call->{prompts.add(call.getArgument(0));return response(prompts.size()==1?"not-json SECRET-TEXT":output().toString());});
        try(var service=service(model,96000,Duration.ofSeconds(5))) {
            var result=service.generate(evaluation(),retrieval());assertEquals(EddDraftService.Status.DRAFT,result.status());
            assertEquals(2,result.metadata().path("attempt_count").asInt());assertTrue(result.metadata().path("repair_attempted").asBoolean());
            assertEquals("MALFORMED_JSON",result.metadata().at("/attempts/0/output_error").asText());
            assertTrue(result.metadata().at("/attempts/1/elapsed_ms").isNumber());assertFalse(result.metadata().toString().contains("SECRET-TEXT"));
            assertTrue(result.draft().path("proposed_grade").isNull());
        }
        String repair=prompts.get(1).getInstructions().get(1).getText();
        assertTrue(repair.contains("format_repair"));assertTrue(repair.contains("MALFORMED_JSON"));assertTrue(repair.contains("previous_output"));
        assertEquals(prompts.get(0).getInstructions().get(0).getText(),prompts.get(1).getInstructions().get(0).getText());
        assertTrue(prompts.get(0).getInstructions().get(0).getText().contains("JSON Schema"));
    }
    @Test void repeatedMalformedOutputStopsAfterTwoAttempts() throws Exception {
        ChatModel model=mock(ChatModel.class);when(model.call(any(Prompt.class))).thenReturn(response("{broken"));
        try(var service=service(model,96000,Duration.ofSeconds(5))) {
            var result=service.generate(evaluation(),retrieval());assertEquals(EddDraftService.Status.INVALID_OUTPUT,result.status());
            assertEquals("MALFORMED_JSON",result.metadata().path("output_error").asText());assertNull(result.draft());
            assertEquals(2,result.metadata().path("attempt_count").asInt());verify(model,times(2)).call(any(Prompt.class));
        }
    }
    @Test void unknownReferenceDoesNotEnterFormatRepair() throws Exception {
        ObjectNode bad=output();((ArrayNode)bad.at("/overview_sections/0/fact_refs")).add("FAKE-FACT");
        ChatModel model=mock(ChatModel.class);when(model.call(any(Prompt.class))).thenReturn(response(bad.toString()));
        try(var service=service(model,96000,Duration.ofSeconds(5))) {
            var result=service.generate(evaluation(),retrieval());assertEquals("INVALID_FACT_REFERENCE",result.metadata().path("output_error").asText());
            assertEquals(1,result.metadata().path("attempt_count").asInt());verify(model,times(1)).call(any(Prompt.class));
        }
    }
    @Test void repairCannotExceedContextBudget() throws Exception {
        ChatModel model=mock(ChatModel.class);when(model.call(any(Prompt.class))).thenReturn(response("bad"));
        int firstSize;
        try(var probe=service(model,96000,Duration.ofSeconds(5))) {firstSize=probe.generate(evaluation(),retrieval()).metadata().path("context_bytes").asInt();}
        clearInvocations(model);
        try(var service=service(model,firstSize+1,Duration.ofSeconds(5))) {
            var result=service.generate(evaluation(),retrieval());assertEquals(EddDraftService.Status.INVALID_OUTPUT,result.status());
            assertEquals("CONTEXT_LIMIT",result.metadata().path("repair_skipped").asText());verify(model,times(1)).call(any(Prompt.class));
        }
    }
    @Test void totalDeadlineIncludesBothCalls() throws Exception {
        ChatModel model=mock(ChatModel.class);var count=new java.util.concurrent.atomic.AtomicInteger();
        when(model.call(any(Prompt.class))).thenAnswer(call->{int attempt=count.incrementAndGet();Thread.sleep(attempt==1?120:250);return response(attempt==1?"bad":output().toString());});
        try(var service=service(model,96000,Duration.ofMillis(300))) {
            var result=service.generate(evaluation(),retrieval());assertEquals(EddDraftService.Status.TIMEOUT,result.status());
            assertEquals(2,result.metadata().path("attempt_count").asInt());assertNull(result.draft());
        }
    }
    @Test void nativeJsonOptionsAreExplicitAndDoNotMutateDefaults() {
        var settings=new EddDraftService.Settings(96000,64000,4096,Duration.ofSeconds(5),1,1,"v2",EddDraftService.OutputMode.JSON_OBJECT);
        ChatModel model=mock(ChatModel.class);
        var deep=org.springframework.ai.deepseek.DeepSeekChatOptions.builder().model("synthetic").build();
        when(model.getDefaultOptions()).thenReturn(deep);
        var options=(org.springframework.ai.deepseek.DeepSeekChatOptions)EddDraftService.options(model,settings);
        assertNotNull(options.getResponseFormat());assertNull(deep.getResponseFormat());assertEquals(4096,options.getMaxTokens());
        var dash=com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions.builder().withModel("synthetic").build();
        when(model.getDefaultOptions()).thenReturn(dash);
        var dashOptions=(com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions)EddDraftService.options(model,settings);
        assertNotNull(dashOptions.getResponseFormat());assertNull(dash.getResponseFormat());
    }
    @Test void nullableSuggestionValueMatchesGeneratedSchemaAndStrictTypes() throws Exception {
        var converter=new EddDraftOutput.Converter();
        JsonNode schema=json.readTree(converter.getJsonSchema());
        assertTrue(schema.findValues("value").stream()
                .anyMatch(n->n.path("type").toString().equals("[\"string\",\"null\"]")));
        var out=output();var recommendation=((ArrayNode)out.path("disposition_recommendations")).addObject()
                .put("status","insufficient_evidence").putNull("value").put("reason","待核实");
        recommendation.putArray("fact_refs");recommendation.putArray("rule_refs");
        assertNull(converter.convert(out.toString()).dispositionRecommendations().get(0).value());
        ((ObjectNode)out.at("/overview_sections/0")).put("text",123);
        assertEquals(EddDraftOutput.ErrorCode.SCHEMA_MISMATCH,assertThrows(EddDraftOutput.InvalidOutput.class,()->converter.convert(out.toString())).code());
    }
    @Test void converterDoesNotLogInvalidModelText() {
        var logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        var events=new ArrayList<String>();
        var appender=new ch.qos.logback.core.AppenderBase<ch.qos.logback.classic.spi.ILoggingEvent>() {
            protected void append(ch.qos.logback.classic.spi.ILoggingEvent e){events.add(e.getFormattedMessage());}
        };
        appender.start();logger.addAppender(appender);
        try {assertThrows(EddDraftOutput.InvalidOutput.class,()->new EddDraftOutput.Converter().convert("SECRET-RAW-TEXT"));}
        finally {logger.detachAppender(appender);appender.stop();}
        assertFalse(events.stream().anyMatch(message->message.contains("SECRET-RAW-TEXT")));
    }
}
