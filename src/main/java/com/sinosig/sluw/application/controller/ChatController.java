package com.sinosig.sluw.application.controller;

import com.sinosig.sluw.application.dto.ChatRequest;
import com.sinosig.sluw.application.dto.ChatResponse;
import com.sinosig.sluw.application.service.KnowledgeService;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 聊天控制器
 * 暴露REST API端点供用户提问
 *
 * @author
 * @version 1.0
 * @since 2024-01-01
 */
@RestController
@RequestMapping("/ai/api")
public class ChatController {

    private static final Logger logger = LoggerFactory.getLogger(ChatController.class);

    @Resource
    private KnowledgeService knowledgeService;

    /**
     * 处理聊天请求
     * @param chatRequest 聊天请求对象
     * @return 聊天响应对象
     */
    @PostMapping("/chat")
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest chatRequest) {
        logger.info("收到聊天请求: {}", chatRequest);

        if (!chatRequest.isValid()) {
            logger.warn("无效的请求: 问题为空");
            ChatResponse errorResponse = new ChatResponse("问题不能为空。", null, null, chatRequest.getConversationId());
            return ResponseEntity.badRequest().body(errorResponse);
        }

        ChatResponse response = knowledgeService.getAnswer(chatRequest);
        response.setRefinedQuestion("");
        response.setRetrievedContext("");

        logger.info("返回响应: {}", response);
        return ResponseEntity.ok(response);
    }
}
