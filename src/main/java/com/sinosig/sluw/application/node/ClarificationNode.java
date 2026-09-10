package com.sinosig.sluw.application.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.sinosig.sluw.application.dto.AgentState;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 澄清节点。
 * <p>职责：分析缺失信息，将引导策略存入 context，由统一回答节点生成澄清问题。</p>
 *
 * @author SinoSig AI Team
 */
@Component
public class ClarificationNode extends BaseNode {

    @Override
    protected Map<String, Object> doProcess(OverAllState state) {
        logger.debug("=== 澄清节点开始 ===");
        AgentState agentState = extractAgentState(state);

        logger.debug("分析低置信度意图，准备澄清策略");
        if (agentState.getContext() == null) {
            agentState.setContext(new HashMap<>());
        }
        agentState.getContext().put("needs_clarification", true);
        agentState.getContext().put("clarification_reason", "置信度低于阈值或意图未知");
        logger.info("澄清策略已存入 Context，原因：{}", agentState.getContext().get("clarification_reason"));

        Map<String, Object> updateMap = new HashMap<>();
        updateMap.put("agent_state", agentState);
        return updateMap;
    }
}