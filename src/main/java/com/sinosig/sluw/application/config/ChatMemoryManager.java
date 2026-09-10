package com.sinosig.sluw.application.config;

import org.springframework.ai.chat.memory.ChatMemory;
//import org.springframework.ai.chat.memory.InMemoryChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatMemoryManager {

    // 使用 ConcurrentHashMap 存储 sessionId -> ChatMemory 的映射
    private final Map<String, ChatMemory> sessionMemoryMap = new ConcurrentHashMap<>();

    /**
     * 获取指定会话的记忆，如果不存在则创建
     * @param sessionId 会话ID
     * @return ChatMemory 实例
     */
    public ChatMemory getMemory(String sessionId) {
        return sessionMemoryMap.computeIfAbsent(sessionId, k -> MessageWindowChatMemory.builder().maxMessages(5).build());
    }

    /**
     * 清除指定会话的记忆
     * @param sessionId 会话ID
     */
    public void clearMemory(String sessionId) {
        sessionMemoryMap.remove(sessionId);
    }

    /**
     * 获取所有活跃会话
     * @return 会话ID集合
     */
    public Set<String> getActiveSessions() {
        return sessionMemoryMap.keySet();
    }
}

