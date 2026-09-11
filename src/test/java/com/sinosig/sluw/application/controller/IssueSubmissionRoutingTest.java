package com.sinosig.sluw.application.controller;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import com.sinosig.sluw.application.dto.ChatRequest;
import com.sinosig.sluw.application.service.*;
import reactor.core.publisher.*;

class IssueSubmissionRoutingTest {
    @Test void trialStreamBypassesAgentAndKeepsDoneProtocol() {
        var agent=mock(AgentService.class);var trial=mock(IssueSubmissionTrialService.class);
        when(trial.answer("json")).thenReturn(Mono.just("trial response"));
        var controller=new AgentController(agent,trial);
        var request=new ChatRequest();request.setQuestion("json");request.setIssueSubmissionTrial(true);
        var events=controller.chatStream(request,new MockHttpServletRequest()).collectList().block();
        assertEquals("trial response",events.get(0).data());assertEquals("[DONE]",events.get(1).data());
        verifyNoInteractions(agent);
    }
    @Test void synchronousTrialBypassesAgent() {
        var agent=mock(AgentService.class);var trial=mock(IssueSubmissionTrialService.class);
        when(trial.answer("json")).thenReturn(Mono.just("trial response"));
        var request=new ChatRequest();request.setQuestion("json");request.setIssueSubmissionTrial(true);
        assertEquals("trial response",new AgentController(agent,trial).chat(request).block().getResponse());
        verifyNoInteractions(agent);
    }
    @Test void defaultRequestStillUsesOrdinaryChat() {
        var agent=mock(AgentService.class);var trial=mock(IssueSubmissionTrialService.class);
        when(agent.chatStream(any(),any(),anyBoolean(),anyBoolean(),any(),any(),anyLong())).thenReturn(Flux.just("normal"));
        var request=new ChatRequest();request.setQuestion("normal question");
        var events=new AgentController(agent,trial).chatStream(request,new MockHttpServletRequest()).collectList().block();
        assertEquals("normal",events.get(0).data()); verifyNoInteractions(trial);
    }
}
