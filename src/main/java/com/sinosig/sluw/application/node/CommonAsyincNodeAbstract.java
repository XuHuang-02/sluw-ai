package com.sinosig.sluw.application.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.sinosig.sluw.application.dto.AgentState;

public abstract class CommonAsyincNodeAbstract implements AsyncNodeAction {

    public AgentState getOrCreateAgentState(OverAllState state) {
        Object obj = state.data().get("agent_state");
        if (obj instanceof AgentState){
            return (AgentState) obj;
        }
        return new AgentState((String) state.data().get("user_input"));
    }
}
