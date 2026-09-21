package com.sinosig.sluw.application.controller;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import com.sinosig.sluw.application.dto.ChatRequest;
import com.sinosig.sluw.application.dto.AgentState;
import com.sinosig.sluw.application.service.AgentService;
import reactor.core.publisher.*;

class IssueSubmissionRoutingTest {
    @Test void legacyTrialFlagCannotRouteStreamAwayFromRiskOverview() {
        var agent=mock(AgentService.class);
        when(agent.chatStream(any(),any(),anyBoolean(),anyBoolean(),any(),any(),anyLong())).thenReturn(Flux.just("风险概述"));
        var request=new ChatRequest();request.setQuestion("客户资料");request.setIssueSubmissionTrial(true);
        var events=new AgentController(agent).chatStream(request,new MockHttpServletRequest()).collectList().block();
        assertEquals("风险概述",events.get(0).data());assertEquals("[DONE]",events.get(1).data());
        verify(agent,times(1)).chatStream(any(),eq("客户资料"),anyBoolean(),anyBoolean(),any(),any(),anyLong());
    }
    @Test void legacyTrialFlagCannotRouteSyncAwayFromRiskOverview() {
        var agent=mock(AgentService.class);var state=new AgentState();state.setResponse("风险概述");
        when(agent.chat(any(),any(),anyBoolean(),anyBoolean())).thenReturn(Mono.just(state));
        var request=new ChatRequest();request.setQuestion("客户资料");request.setIssueSubmissionTrial(true);
        assertEquals("风险概述",new AgentController(agent).chat(request).block().getResponse());
        verify(agent,times(1)).chat(any(),eq("客户资料"),anyBoolean(),anyBoolean());
    }
    @Test void emptyStreamRequestIsRejectedWithoutCallingModelService() {
        var agent=mock(AgentService.class);
        assertThrows(IllegalArgumentException.class,()->new AgentController(agent).chatStream(null,new MockHttpServletRequest()).collectList().block());
        verifyNoInteractions(agent);
    }
}
