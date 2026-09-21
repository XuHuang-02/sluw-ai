package com.sinosig.sluw.application.controller;

import com.sinosig.sluw.application.dto.AgentState;
import com.sinosig.sluw.application.dto.ChatRequest;
import com.sinosig.sluw.application.service.AgentService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 智能体 REST 控制器。
 * <p>提供同步和流式两种对话接口。</p>
 *
 * @author SinoSig AI Team
 */
@RestController
@RequestMapping("/api/agent")
@CrossOrigin(origins = "*")
public class AgentController {

    private static final Logger logger = LoggerFactory.getLogger(AgentController.class);
    private final AgentService agentService;

    public AgentController(AgentService agentService) {
        this.agentService = agentService;
    }

    /**
     * 普通聊天接口（同步）。
     *
     * @param request 聊天请求
     * @return 最终智能体状态（包含回复）
     */
    @PostMapping("/chat")
    public Mono<AgentState> chat(@RequestBody ChatRequest request) {
        if (request == null || !request.isValid()) {
            return Mono.error(new IllegalArgumentException("Question cannot be empty"));
        }
        String question = request.getQuestion();
        String conversationId = request.getConversationId();
        String userId = request.getUserId();
        boolean useRefiner = request.isUseRefiner();
        boolean useRefinerMemory = request.isUseRefinerMemory();

        logger.info("收到同步聊天请求：User={}, ConvId={}, QuestionLen={}, useRefiner={}, useRefinerMemory={}",
                userId, conversationId, question.length(), useRefiner, useRefinerMemory);
        long startTime = System.currentTimeMillis();
        return agentService.chat(conversationId, question, useRefiner, useRefinerMemory)
                .doOnSuccess(s -> logger.info("同步请求完成，耗时 {} ms", System.currentTimeMillis() - startTime))
                .doOnError(e -> logger.error("同步请求失败", e));
    }

    /**
     * 流式聊天接口（SSE）。
     *
     * @param request 聊天请求
     * @return SSE 流，每个事件包含一段内容，最后发送 [DONE]
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> chatStream(@RequestBody ChatRequest request, HttpServletRequest httpRequest) {
        if (request == null || !request.isValid()) {
            return Flux.error(new IllegalArgumentException("Question cannot be empty"));
        }
        //从Cookie中获取用户Token
        String userToken = getUserToken(httpRequest);
        String question = request.getQuestion();
        String conversationId = request.getConversationId();
        String userId = request.getUserId();
        boolean useRefiner = request.isUseRefiner();
        boolean useRefinerMemory = request.isUseRefinerMemory();
        String messageId = request.getMessageId();

        logger.info("收到流式聊天请求：User={}, ConvId={}, QuestionLen={}, useRefiner={}, useRefinerMemory={}",
                userId, conversationId, question != null ? question.length() : 0, useRefiner, useRefinerMemory);
        long startTime = System.currentTimeMillis();

        Flux<String> responseStream = agentService.chatStream(conversationId, question, useRefiner, useRefinerMemory, userToken, messageId, startTime);

        return responseStream
                .map(content -> {
                    // 在第一个数据块中，将messageId拼接返回
                    // 这里使用一个简单约定：第一个数据块为特殊格式 `{"msgId":"xxx","data":"实际内容"}`
                    return ServerSentEvent.<String>builder().data(content).build();
                })
                .concatWith(Flux.just(ServerSentEvent.<String>builder().data("[DONE]").build()))
                .onErrorResume(e -> {
                    logger.error("流式请求异常", e);
                    return Flux.just(ServerSentEvent.<String>builder().data("[ERROR] 系统繁忙").build());
                })
                .doOnComplete(() -> logger.info("流式请求正常结束，耗时 {} ms", System.currentTimeMillis() - startTime))
                .doOnError(e -> logger.error("流式请求异常结束：ConvId={}", conversationId, e));
    }

    private String getUserToken(HttpServletRequest httpRequest){
        String userToken = "";
        Cookie[] cookies = httpRequest.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("USERINFO_TOKEN".equals(cookie.getName())) {
                    userToken = cookie.getValue();
                    break;
                }
            }
        }
        return userToken;
    }
}