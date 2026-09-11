package com.sinosig.sluw.application.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.mockito.ArgumentCaptor;
import java.util.List;

class IssueSubmissionTrialServiceTest {
    private ChatModel model() {
        ChatModel model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(
                "处理去向：测试替身结果；命中的代码条件：无；引用的输入记录：无；缺失信息：无")))));
        return model;
    }
    @Test void eachRequestHasOnlySystemAndCurrentJson() throws Exception {
        ChatModel model=model(); var service=new IssueSubmissionTrialService(model);
        service.answer("{\"contno\":\"FIRST\",\"completeness\":{}}").block();
        service.answer("{\"contno\":\"SECOND\",\"completeness\":{}}").block();
        var capture=ArgumentCaptor.forClass(Prompt.class); verify(model,times(2)).call(capture.capture());
        Prompt second=capture.getAllValues().get(1);
        assertEquals(2,second.getInstructions().size());
        assertTrue(second.getInstructions().get(1).getText().contains("SECOND"));
        assertFalse(second.getInstructions().get(1).getText().contains("FIRST"));
        assertTrue(second.getInstructions().get(0).getText().contains("count"));
    }
    @Test void invalidJsonDoesNotCallModel() throws Exception {
        ChatModel model=model(); var service=new IssueSubmissionTrialService(model);
        for(String input:List.of("not JSON","[]","{}",
                "{\"contno\":\"a\",\"contno\":\"b\",\"completeness\":{}}",
                "{\"contno\":\"a\",\"completeness\":{}} {}",
                "{\"contno\":\"a\",\"completeness\":{},\"historyErrors\":true}")) {
            assertTrue(service.answer(input).block().contains("未执行"));
        }
        verify(model,never()).call(any(Prompt.class));
    }
    @Test void missingHistoryIsNotConvertedIntoAnEmptyArray() throws Exception {
        ChatModel model=model(); var service=new IssueSubmissionTrialService(model);
        service.answer("{\"contno\":\"a\",\"completeness\":{},\"historyErrors\":null}").block();
        var c=ArgumentCaptor.forClass(Prompt.class);verify(model).call(c.capture());
        assertTrue(c.getValue().getInstructions().get(1).getText().contains("\"historyErrors\":null"));
    }
    @Test void serviceFailureDoesNotExposeExceptionOrInventRoute() throws Exception {
        ChatModel model=model();when(model.call(any(Prompt.class))).thenThrow(new RuntimeException("SECRET"));
        String answer=new IssueSubmissionTrialService(model).answer("{\"contno\":\"a\",\"completeness\":{}}").block();
        assertTrue(answer.contains("暂无法确定"));assertFalse(answer.contains("SECRET"));
    }

    @Test void preparationUsesOnlyItsOwnCodeAndCurrentSnapshot() throws Exception {
        ChatModel model=model(); var service=new IssueSubmissionTrialService(model);
        String input=new org.springframework.core.io.ClassPathResource("static/trial/send-preparation-example.json")
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        service.answer(input).block();
        var c=ArgumentCaptor.forClass(Prompt.class);verify(model).call(c.capture());
        String system=c.getValue().getInstructions().get(0).getText();
        assertTrue(system.contains("417:"));
        assertTrue(system.contains("589:"));
        assertFalse(system.contains("138:"));
        assertEquals(new com.fasterxml.jackson.databind.ObjectMapper().readTree(input),
                new com.fasterxml.jackson.databind.ObjectMapper().readTree(c.getValue().getInstructions().get(1).getText()));
    }

    @Test void unsupportedStageAndMalformedPreparationRowsNeverCallModel() throws Exception {
        ChatModel model=model(); var service=new IssueSubmissionTrialService(model);
        for (String extra : List.of(
                "\"experimentStage\":\"AUTO_PASS\"",
                "\"experimentStage\":null",
                "\"candidateErrors\":{}",
                "\"candidateErrors\":[true]",
                "\"lcissuepol\":[null]",
                "\"entryConfirmed\":\"true\"")) {
            String input="{\"contno\":\"a\",\"completeness\":{},"+extra+"}";
            assertTrue(service.answer(input).block().contains("未执行"));
        }
        verify(model,never()).call(any(Prompt.class));
    }

    @Test void missingPreparationDataIsNotInvented() throws Exception {
        ChatModel model=model(); var service=new IssueSubmissionTrialService(model);
        String input=new org.springframework.core.io.ClassPathResource("static/trial/send-preparation-template.json")
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        service.answer(input).block();
        var c=ArgumentCaptor.forClass(Prompt.class);verify(model).call(c.capture());
        var payload=new com.fasterxml.jackson.databind.ObjectMapper().readTree(c.getValue().getInstructions().get(1).getText());
        assertTrue(payload.get("entryConfirmed").isNull());
        assertTrue(payload.get("lcissuepol").isNull());
        assertFalse(payload.path("completeness").path("lcissuepol").asBoolean());
    }

    @Test void allPreparationFixturesAreAcceptedWithoutHardcodedDecisions() throws Exception {
        ChatModel model=model(); var service=new IssueSubmissionTrialService(model);
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        var cases=mapper.readTree(java.nio.file.Files.readString(
                java.nio.file.Path.of("docs/trial/send-preparation-cases.json")));
        for(var sample:cases) {
            String answer=service.answer(mapper.writeValueAsString(sample.get("input"))).block();
            assertTrue(answer.contains("测试替身结果"));
        }
        verify(model,times(8)).call(any(Prompt.class));
    }
}
