package com.sinosig.sluw.application.config;

import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.sinosig.sluw.application.dto.AgentState;
import com.sinosig.sluw.application.dto.IntentType;
import com.sinosig.sluw.application.node.*;
import com.sinosig.sluw.application.node.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 智能体工作流图配置类。
 * <p>定义节点、边和条件路由，构建完整的工作流图，用于生产环境。</p>
 *
 * <p>工作流图结构：</p>
 * <pre>
 * START → refiner → analyzer ──(条件路由)──→ clarifier ──┐
 *                               ├→ knowledge_agent ──┤
 *                               ├→ tool_agent ──────┤
 *                               └→ chat_agent ──────┘
 *                                                    ↓
 *                                          response_generator → END
 * </pre>
 *
 * @author SinoSig AI Team
 */
@Configuration
public class AgentGraphConfig {

    private static final Logger logger = LoggerFactory.getLogger(AgentGraphConfig.class);

    @Value("${agent.confidence.threshold:0.65}")
    private double confidenceThreshold;

    /** 意图到节点名称的映射，支持动态路由扩展 */
    private static final Map<IntentType, String> INTENT_NODE_MAP = new ConcurrentHashMap<>();

    static {
        INTENT_NODE_MAP.put(IntentType.KNOWLEDGE_QUERY, "knowledge_agent");
        INTENT_NODE_MAP.put(IntentType.TOOL_EXECUTION, "tool_agent");
        INTENT_NODE_MAP.put(IntentType.CHIT_CHAT, "chat_agent");
        // UNKNOWN 和置信度不足的场景会路由到 clarifier
    }

    /**
     * 创建并配置工作流图。
     *
     * @param refinerNode           问题润色节点
     * @param analyzerNode          意图分析节点
     * @param clarificationNode     澄清节点
     * @param knowledgeNode         知识检索节点
     * @param toolNode              工具执行节点
     * @param chatNode              闲聊节点
     * @param responseGeneratorNode 统一回答生成节点（注意：此节点不再调用LLM，仅做上下文聚合）
     * @return 配置好的 StateGraph 实例
     * @throws GraphStateException 图构建异常
     */
    @Bean
    public StateGraph agentWorkflow(
            QuestionRefinerNode refinerNode,
            IntentAnalyzerNode analyzerNode,
            ClarificationNode clarificationNode,
            KnowledgeRetrievalNode knowledgeNode,
            ToolExecutorNode toolNode,
            ChatNode chatNode,
            ResponseGeneratorNode responseGeneratorNode) throws GraphStateException {

        logger.info("开始构建智能体工作流图");
        StateGraph graph = new StateGraph();

        // 注册所有节点
        graph.addNode("refiner", refinerNode);
        graph.addNode("analyzer", analyzerNode);
        graph.addNode("clarifier", clarificationNode);
        graph.addNode("knowledge_agent", knowledgeNode);
        graph.addNode("tool_agent", toolNode);
        graph.addNode("chat_agent", chatNode);
        graph.addNode("response_generator", responseGeneratorNode);
        logger.debug("节点注册完成：refiner, analyzer, clarifier, knowledge_agent, tool_agent, chat_agent, response_generator");

        // 入口：START -> refiner -> analyzer
        graph.addEdge(StateGraph.START, "refiner");
        graph.addEdge("refiner", "analyzer");
        logger.debug("入口边设置：START -> refiner -> analyzer");

        // 条件路由：根据意图分析结果决定下一个节点
        graph.addConditionalEdges("analyzer",
                state -> CompletableFuture.completedFuture(determineNextNode(state)),
                Map.ofEntries(
                        Map.entry("clarifier", "clarifier"),
                        Map.entry("knowledge_agent", "knowledge_agent"),
                        Map.entry("tool_agent", "tool_agent"),
                        Map.entry("chat_agent", "chat_agent")
                )
        );
        logger.debug("条件路由配置完成，路由决策基于意图类型和置信度");

        // 所有分支最终汇聚到统一回答生成节点（仅做上下文聚合）
        graph.addEdge("clarifier", "response_generator");
        graph.addEdge("knowledge_agent", "response_generator");
        graph.addEdge("tool_agent", "response_generator");
        graph.addEdge("chat_agent", "response_generator");
        logger.debug("汇聚边配置完成：所有分支节点 -> response_generator");

        // 结束边
        graph.addEdge("response_generator", StateGraph.END);
        logger.debug("结束边配置：response_generator -> END");

        logger.info("智能体工作流图构建完成");
        return graph;
    }

    /**
     * 编译工作流图，生成可执行的 CompiledGraph。
     *
     * @param agentWorkflow 配置好的 StateGraph
     * @return 编译后的 CompiledGraph 实例
     * @throws GraphStateException 图编译异常
     */
    @Bean
    public CompiledGraph compiledAgentGraph(StateGraph agentWorkflow) throws GraphStateException {
        logger.info("开始编译智能体工作流图");
        CompiledGraph compiled = agentWorkflow.compile();
        logger.info("智能体工作流图编译完成");
        return compiled;
    }

    /**
     * 根据意图分析结果确定下一个节点（路由决策核心，支持可扩展映射）。
     *
     * @param state 当前图状态
     * @return 下一个节点名称
     */
    private String determineNextNode(OverAllState state) {
        Object obj = state.data().get("agent_state");
        if (!(obj instanceof AgentState s)) {
            logger.warn("AgentState 缺失，默认路由至 clarifier");
            return "clarifier";
        }

        double score = s.getConfidenceScore() != null ? s.getConfidenceScore() : 0.0;
        IntentType intent = s.getIntentType();

        logger.debug("路由决策：Intent={}, Score={}", intent, score);

        // 置信度不足或意图未知，进入澄清节点
        if (intent == IntentType.UNKNOWN || score < confidenceThreshold) {
            logger.info("置信度不足或意图未知，路由至澄清节点");
            return "clarifier";
        }

        // 从映射表中获取节点，默认 clarifier
        String nextNode = INTENT_NODE_MAP.getOrDefault(intent, "clarifier");
        logger.info("意图={}，路由至节点：{}", intent, nextNode);
        return nextNode;
    }
}