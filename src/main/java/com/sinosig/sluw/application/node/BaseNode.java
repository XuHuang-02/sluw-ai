package com.sinosig.sluw.application.node;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.sinosig.sluw.application.dto.AgentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 所有节点的抽象基类。
 * <p>实现 AsyncNodeAction 接口，提供公共方法用于提取 AgentState，
 * 子类实现同步 doProcess 方法，apply 方法内部使用 Reactor 调度并转换为 CompletableFuture。</p>
 * <p>异常处理：捕获子类异常后，仅记录一次错误日志，并重新抛出 RuntimeException，
 * 避免多层包装导致日志重复。上层 AgentService 会统一处理并返回友好提示。</p>
 *
 * @author SinoSig AI Team
 */
public abstract class BaseNode implements AsyncNodeAction {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    /**
     * 从 OverAllState 中提取 AgentState。
     *
     * @param state 图状态对象
     * @return AgentState 实例
     */
    protected AgentState extractAgentState(OverAllState state) {
        Object obj = state.data().get("agent_state");
        if (obj instanceof AgentState) {
            logger.trace("从 state 中获取到已存在的 AgentState");
            return (AgentState) obj;
        }
        String userInput = (String) state.data().get("user_input");
        if (userInput == null) {
            userInput = "";
        }
        logger.debug("state 中无 AgentState，创建新实例，userInput={}", userInput);
        AgentState newState = new AgentState(userInput);
        if (newState.getContext() == null) {
            newState.setContext(new HashMap<>());
        }
        return newState;
    }

    /**
     * 子类实现具体业务逻辑，返回要更新的状态字段。
     * 注意：该方法运行在 boundedElastic 线程池中，不会阻塞主线程。
     *
     * @param state 图状态对象
     * @return 需要更新的状态映射
     * @throws Exception 业务异常
     */
    protected abstract Map<String, Object> doProcess(OverAllState state) throws Exception;

    /**
     * AsyncNodeAction 接口方法，将同步的 doProcess 包装为 CompletableFuture。
     * 内部使用 Reactor 的弹性线程池执行，避免阻塞。
     *
     * @param state 图状态对象
     * @return 包含更新映射的 CompletableFuture
     */
    @Override
    public CompletableFuture<Map<String, Object>> apply(OverAllState state) {
        return Mono.fromCallable(() -> doProcess(state))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(e -> logger.error("节点执行失败: {}", e.getMessage(), e))
                .toFuture();
    }
}