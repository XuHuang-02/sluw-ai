package com.sinosig.sluw.application.service;

import com.sinosig.sluw.application.config.PromptTemplateConfig;
import com.sinosig.sluw.application.dto.AgentState;
import com.sinosig.sluw.application.node.ResponseGeneratorNode;
import com.sinosig.sluw.application.commons.service.AssistantTrackService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.data.redis.core.RedisTemplate;
import reactor.core.publisher.Flux;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RiskOverviewChatTest {
    @Test void syncUsesOneModelCallAndKeepsOriginalFactsAndHistory() {
        ChatModel model=mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("风险概述")))));
        var memory=mock(AgentStateMemoryService.class);var state=new AgentState();
        state.addHistory("user","同一客户此前提供职业资料");
        state.getContext().put("clarification_response","旧路由回复");
        when(memory.loadState("C1")).thenReturn(state);
        var generator=new ResponseGeneratorNode(ChatClient.builder(model).build(),new PromptTemplateConfig());
        var service=new AgentService(memory,generator,mock(RedisTemplate.class),mock(AssistantTrackService.class));
        String input="保费10万元；黑名单未查询；原文包含{{history}}";
        var result=service.chat("C1",input,true,true).block();
        assertNotNull(result);assertEquals("风险概述",result.getResponse());assertEquals(input,result.getUserInput());
        var prompt=org.mockito.ArgumentCaptor.forClass(Prompt.class);verify(model,times(1)).call(prompt.capture());
        String text=prompt.getValue().getContents();
        assertTrue(text.contains(input));assertTrue(text.contains("同一客户此前提供职业资料"));assertFalse(text.contains("旧路由回复"));
        assertFalse(text.contains("CHIT_CHAT"));assertFalse(text.contains("KNOWLEDGE_QUERY"));
        verify(memory).saveState("C1",result);
    }
    @Test void streamUsesOneModelCallWithoutRefiningOrRouting() {
        ChatModel model=mock(ChatModel.class);
        when(model.stream(any(Prompt.class))).thenReturn(Flux.just(new ChatResponse(List.of(new Generation(new AssistantMessage("风险概述"))))));
        var memory=mock(AgentStateMemoryService.class);
        var generator=new ResponseGeneratorNode(ChatClient.builder(model).build(),new PromptTemplateConfig());
        var service=new AgentService(memory,generator,mock(RedisTemplate.class),mock(AssistantTrackService.class));
        var chunks=service.chatStream("C2","年收入20万元",true,false,"","M1",System.currentTimeMillis()).collectList().block();
        assertNotNull(chunks);assertTrue(String.join("",chunks).contains("风险概述"));
        verify(model,times(1)).stream(any(Prompt.class));verify(model,never()).call(any(Prompt.class));
        var saved=org.mockito.ArgumentCaptor.forClass(AgentState.class);verify(memory).saveState(eq("C2"),saved.capture());
        assertEquals("风险概述",saved.getValue().getResponse());assertEquals("年收入20万元",saved.getValue().getHistory().get(0).get("content"));
    }
}
