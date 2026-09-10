package com.sinosig.sluw.application.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.Map;


@Service
public class DeepSeekChatService {

    private final ChatClient chatClient;

    public DeepSeekChatService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    @Value("${chat.system.prompt:你是一个AI助手，请直接回答用户问题。要求：使用纯文本格式，不要使用任何Markdown符号。回答要简洁明了，避免冗长解释。}")
    private String systemPrompt;
    /**
     * 流式聊天接口
     * @param userInput 用户输入
     * @return 返回AI生成的文本流
     */
    public Flux<String> streamChat(String userInput) {
        // 1. 构建完整提示词（纯文本格式）
        String fullPrompt = buildFullPrompt(userInput);

        // 2. 直接传入纯文本（避免模板解析问题）
        return chatClient.prompt(fullPrompt)
                .stream()
                .content();
    }

    /**
     * 构建完整的提示词
     * @param userInput 用户输入
     * @return 合并系统指令和用户问题的字符串
     */
    private String buildFullPrompt(String userInput) {
        // 使用StringBuilder高效拼接多行文本
        return new StringBuilder()
                .append("系统指令：")
                .append(systemPrompt)
                .append("\n\n用户问题：")
                .append(userInput)
                .append("\n\n请根据以上系统指令回答用户问题。")
                .toString();
    }
}
