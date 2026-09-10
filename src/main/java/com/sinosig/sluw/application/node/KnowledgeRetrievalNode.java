package com.sinosig.sluw.application.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.sinosig.sluw.application.client.RagFlowClient;
import com.sinosig.sluw.application.dto.AgentState;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 知识检索节点。
 * <p>职责：检索知识库，将原始文档存入 context，不生成回复。</p>
 * <p>使用 RagFlowClient 进行知识库检索，支持根据历史上下文增强查询。</p>
 *
 * @author SinoSig AI Team
 */
@Component
public class KnowledgeRetrievalNode extends BaseNode {

    private final RagFlowClient ragFlowClient;

    public KnowledgeRetrievalNode(RagFlowClient ragFlowClient) {
        this.ragFlowClient = ragFlowClient;
    }

    @Override
    protected Map<String, Object> doProcess(OverAllState state) {
        logger.debug("=== 知识检索节点开始 ===");
        AgentState agentState = extractAgentState(state);

        String query = agentState.getUserInput();
        if (query == null || query.trim().isEmpty()) {
            logger.warn("用户输入为空，跳过知识检索");
            return buildUpdateMap(agentState);
        }

        logger.debug("执行检索策略，Query: {}", query);

        String retrievedContext;
        try {
            retrievedContext = ragFlowClient.retrieve(query);

        } catch (Exception e) {
            logger.error("知识检索异常: {}", e.getMessage(), e);
            retrievedContext = "检索服务暂时不可用";
        }

        logger.info("知识检索完成，检索结果长度: {}", retrievedContext != null ? retrievedContext.length() : 0);

        if (agentState.getContext() == null) {
            agentState.setContext(new HashMap<>());
        }
        logger.debug(retrievedContext);
        agentState.getContext().put("retrieved_documents", retrievedContext);
        logger.debug("检索结果已存入 context，key: retrieved_documents");

        return buildUpdateMap(agentState);
    }

    private Map<String, Object> buildUpdateMap(AgentState agentState) {
        Map<String, Object> updateMap = new HashMap<>();
        updateMap.put("agent_state", agentState);
        return updateMap;
    }
}