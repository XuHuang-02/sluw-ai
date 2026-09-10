package com.sinosig.sluw.application.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.sinosig.sluw.application.dto.AgentState;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 闲聊处理节点。
 * <p>职责：标记为闲聊模式，将原始输入传入 context，由统一回答节点生成回复。</p>
 *
 * @author SinoSig AI Team
 */
@Component
public class ChatNode extends BaseNode {

    @Override
    protected Map<String, Object> doProcess(OverAllState state) {
        logger.debug("=== 闲聊处理节点开始 ===");
        AgentState agentState = extractAgentState(state);

        if (agentState.getContext() == null) {
            agentState.setContext(new HashMap<>());
        }
        agentState.getContext().put("chat_mode", true);
        logger.info("闲聊模式标记完成，用户输入：{}", agentState.getUserInput());

        Map<String, Object> updateMap = new HashMap<>();
        updateMap.put("agent_state", agentState);
        return updateMap;
    }
}