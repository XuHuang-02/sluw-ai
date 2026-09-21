package com.sinosig.sluw.application.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sinosig.sluw.application.commons.entity.AssistantTrackEntity;
import com.sinosig.sluw.application.commons.service.AssistantTrackService;
import com.sinosig.sluw.application.commons.utils.DateUtil;
import com.sinosig.sluw.application.commons.utils.Strings;
import com.sinosig.sluw.application.dto.AgentState;
import com.sinosig.sluw.application.node.ResponseGeneratorNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 智能体对外服务类。
 * <p>提供同步和流式两种对话入口，直接调用风险概述生成器。</p>
 * <p>异常处理：统一捕获生成过程中的异常，记录一次错误日志，返回友好提示。</p>
 *
 * @author SinoSig AI Team
 */
@Service
public class AgentService {

    private static final Logger logger = LoggerFactory.getLogger(AgentService.class);
    private static final String MODEL_BUSY_MESSAGE = "生成暂时失败，请稍后重试。";

    private final AgentStateMemoryService agentStateMemoryService;
    private final ResponseGeneratorNode responseGeneratorNode;
    private final RedisTemplate<String, Object> redisTemplate;
    private final AssistantTrackService assistantTrackService;

    public AgentService(AgentStateMemoryService agentStateMemoryService,
                        ResponseGeneratorNode responseGeneratorNode,
                        RedisTemplate<String, Object> redisTemplate,
                        AssistantTrackService assistantTrackService) {
        this.agentStateMemoryService = agentStateMemoryService;
        this.responseGeneratorNode = responseGeneratorNode;
        this.redisTemplate = redisTemplate;
        this.assistantTrackService = assistantTrackService;
    }

    /**
     * 同步聊天接口。
     */
    public Mono<AgentState> chat(String conversationId, String userInput,
                                 boolean useRefiner, boolean useRefinerMemory) {
        String traceId = UUID.randomUUID().toString();
        logger.info("[{}] === 同步聊天流程开始 ===, conversationId={}, useRefiner={}, useRefinerMemory={}",
                traceId, conversationId, useRefiner, useRefinerMemory);

        AgentState currentState = prepareInitialState(conversationId, userInput, useRefiner, useRefinerMemory);
        return Mono.fromCallable(() -> {
                    AgentState generatedState = currentState;
                    String finalResponse = responseGeneratorNode.generateSync(generatedState);
                    generatedState.setResponse(finalResponse);

                    String actualUserInput = generatedState.getUserInput();
                    generatedState.addHistory("user", actualUserInput);
                    generatedState.addHistory("assistant", finalResponse);

                    agentStateMemoryService.saveState(conversationId, generatedState);
                    logger.info("[{}] 同步聊天完成，回复长度={}, Token 消耗 - 输入: {}, 输出: {}, 总计: {}",
                            traceId, finalResponse.length(),
                            generatedState.getTotalPromptTokens(),
                            generatedState.getTotalCompletionTokens(),
                            generatedState.getTotalTokens());
                    return generatedState;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> {
                    logger.error("[{}] 同步聊天失败: {}", traceId, e.getMessage());
                    AgentState errorState = createModelBusyErrorState(userInput);
                    agentStateMemoryService.saveState(conversationId, errorState);
                    return Mono.just(errorState);
                });
    }

    /**
     * 流式聊天接口（SSE）。
     */
    public Flux<String> chatStream(String conversationId, String userInput,
                                   boolean useRefiner, boolean useRefinerMemory, String userToken, String messageId, long startTime) {
        String traceId = UUID.randomUUID().toString();
        logger.info("[{}] === 流式聊天流程开始 ===, conversationId={}, messageId={}",
                traceId, conversationId, messageId);

        Mono<AgentState> stateMono = Mono.just(prepareInitialState(conversationId, userInput, useRefiner, useRefinerMemory))
                .map(state -> {
                    // 可以在这里将messageId存入AgentState的上下文中，以备后用
                    state.getContext().put("message_id", messageId);
                    return state;
                })
                .onErrorResume(e -> {
                    logger.error("[{}] 资料准备失败: {}", traceId, e.getMessage());
                    AgentState errorState = createModelBusyErrorState(userInput);
                    agentStateMemoryService.saveState(conversationId, errorState);
                    saveAssistantTrack(userInput,MODEL_BUSY_MESSAGE+e.getMessage(),conversationId,userToken,messageId,startTime,0,0,0);
                    return Mono.just(errorState);
                });

        return stateMono.flatMapMany(agentState -> {
            if (MODEL_BUSY_MESSAGE.equals(agentState.getResponse())) {
                //return Flux.just(MODEL_BUSY_MESSAGE);
                return Flux.just(agentState.getResponse());
            }

            Flux<String> tokenStream = responseGeneratorNode.generateStream(agentState);
            StringBuilder fullResponse = new StringBuilder();

            // 使用自定义操作符，在流开始时先发送包含messageId的特殊数据块
            return tokenStream
                    .index() // 为每个数据块添加索引
                    .map(tuple -> {
                        long index = tuple.getT1(); // 索引从0开始
                        String token = tuple.getT2();
                        if (index == 0) {
                            // 第一个数据块，拼接messageId
                            // 格式示例：`__MESSAGE_ID__:${messageId}__CONTENT_START__${token}`
                            // 或者使用JSON格式，前端更容易解析
                            return String.format("{\"msgId\":\"%s\",\"data\":\"%s\"}", messageId, escapeJsonString(token));
                        }
                        return token;
                    })
                    .doOnNext(token -> {
                        // 累加响应内容，但不包括第一个数据块的特殊前缀
                        if (!token.startsWith("{\"msgId\":")) {
                            fullResponse.append(token);
                        } else {
                            // 如果是第一个特殊块，只提取data部分进行累加
                            // 注意：这里简化处理，实际应该解析JSON
                            String dataPart = token.substring(token.indexOf("\"data\":\"") + 8, token.lastIndexOf("\""));
                            fullResponse.append(unescapeJsonString(dataPart));
                        }
                    })
                    .doOnComplete(() -> {
                        String finalResponse = fullResponse.toString();
                        agentState.setResponse(finalResponse);
                        String actualUserInput = agentState.getUserInput();
                        agentState.addHistory("user", actualUserInput);
                        agentState.addHistory("assistant", finalResponse);
                        agentStateMemoryService.saveState(conversationId, agentState);
                        logger.info("[{}] 流式聊天完成，回复长度={}, Token 消耗 - 输入: {}, 输出: {}, 总计: {}",
                                traceId, finalResponse.length(),
                                agentState.getTotalPromptTokens(),
                                agentState.getTotalCompletionTokens(),
                                agentState.getTotalTokens());
                        //数据入库
                        saveAssistantTrack(userInput, finalResponse, conversationId, userToken, messageId, startTime, agentState.getTotalPromptTokens(), agentState.getTotalCompletionTokens(), agentState.getTotalTokens());
                    })
                    .doOnError(e -> {
                        logger.error("[{}] 流式生成错误: {}", traceId, e.getMessage());
                        agentState.setResponse(MODEL_BUSY_MESSAGE);
                        agentState.addHistory("user", agentState.getUserInput());
                        agentState.addHistory("assistant", MODEL_BUSY_MESSAGE);
                        agentStateMemoryService.saveState(conversationId, agentState);
                        saveAssistantTrack(userInput,MODEL_BUSY_MESSAGE+e.getMessage(),conversationId,userToken,messageId,startTime, agentState.getTotalPromptTokens(), agentState.getTotalCompletionTokens(), agentState.getTotalTokens());
                    });
        });
    }

    // 辅助方法：简单转义JSON字符串中的特殊字符
    private String escapeJsonString(String input) {
        return input.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private String unescapeJsonString(String input) {
        return input.replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }

    /**
     * 准备初始状态：加载已有会话或创建新会话，设置用户输入、润色开关等参数，
     * 并重置与上一次对话相关的临时上下文。
     *
     * @param conversationId   会话标识
     * @param userInput        用户原始输入
     * @param useRefiner       是否启用输入润色
     * @param useRefinerMemory 是否启用润色记忆
     * @return 初始化后的 AgentState 对象
     * @throws IllegalArgumentException 当 userInput 为空或仅包含空白字符时
     */
    private AgentState prepareInitialState(String conversationId, String userInput,
                                           boolean useRefiner, boolean useRefinerMemory) {
        if (!StringUtils.hasText(userInput)) {
            throw new IllegalArgumentException("User input cannot be empty");
        }
        AgentState state = agentStateMemoryService.loadState(conversationId);
        if (state == null) {
            state = new AgentState();
            logger.debug("新建会话: {}", conversationId);
        }
        state.setOriginalInput(userInput);
        state.setUserInput(userInput);
        state.setUseRefiner(false);
        state.setUseRefinerMemory(useRefinerMemory);
        state.setResponse(null);
        state.getContext().remove("retrieved_documents");
        state.getContext().remove("tool_execution_result");
        state.getContext().remove("needs_clarification");
        return state;
    }

    /**
     * 创建模型繁忙错误状态。
     *
     * @param userInput 用户原始输入
     * @return 填充好错误回复的 AgentState
     */
    private AgentState createModelBusyErrorState(String userInput) {
        AgentState errorState = new AgentState();
        errorState.setOriginalInput(userInput);
        errorState.setUserInput(userInput);
        errorState.setResponse(MODEL_BUSY_MESSAGE);
        errorState.addHistory("user", userInput);
        errorState.addHistory("assistant", MODEL_BUSY_MESSAGE);
        return errorState;
    }


    public void saveAssistantTrack(String question,String Answer,String conversationId,String userToken,String messageId,long startTime,int totalPromptTokens,int totalCompletionTokens,int totalTokens){
        //数据入库
        try {
            AssistantTrackEntity track = new AssistantTrackEntity();
            track.setConversationId(conversationId);
            track.setQuestion(question);
            track.setAnswer(Answer);

            if (Strings.isNoEmpty(userToken)){
                String redisKey = "user:info:" + userToken;
                Object userInfo = redisTemplate.opsForValue().get(redisKey);
                if (userInfo != null){
                    ObjectMapper mapper = new ObjectMapper();
                    Map<String, String> userInfoMap = mapper.readValue((String) userInfo, new TypeReference<Map<String, String>>() {});
                    String userCode = userInfoMap.get("userCode"); //工号
                    String userType = userInfoMap.get("userType"); //用户类型
                    String manageCom = userInfoMap.get("manageCom"); //机构代码
                    track.setUserCode(userCode);
                    track.setUserType(userType);
                    track.setManageCom(manageCom);
                }
            }
            track.setMassageId(messageId);
            track.setDuration(Math.toIntExact(System.currentTimeMillis() - startTime));
            track.setTotalPromptTokens(totalPromptTokens);
            track.setTotalCompletionTokens(totalCompletionTokens);
            track.setTotalTokens(totalTokens);
            track.setMakeDate(DateUtil.date());
            track.setMakeTime(DateUtil.ftime());
            track.setModifyDate(DateUtil.date());
            track.setModifyTime(DateUtil.ftime());

            // 调用异步保存服务
            assistantTrackService.save(track);
            logger.info("对话轨迹已提交异步保存，conversationId: {}", conversationId);
        } catch (Exception e) {
            logger.error("对话轨迹异步保存失败", e);
        }
    }
}