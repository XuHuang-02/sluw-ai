package com.sinosig.sluw.application.service;

import com.sinosig.sluw.application.dto.AgentState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Redis Agent 状态服务。
 * <p>负责将 AgentState 持久化到 Redis，实现多轮对话的状态管理。</p>
 *
 * @author SinoSig AI Team
 */
@Service
public class AgentStateMemoryService {

    private static final Logger logger = LoggerFactory.getLogger(AgentStateMemoryService.class);
    private static final String SESSION_KEY_PREFIX = "agent:session:";

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${app.redis.session-ttl:3600}")
    private long sessionTtl;

    /**
     * 保存 Agent 状态到 Redis。
     *
     * @param conversationId 会话ID
     * @param state          Agent状态
     */
    public void saveState(String conversationId, AgentState state) {
        if (conversationId == null || conversationId.trim().isEmpty()) {
            logger.warn("会话ID为空，跳过 Redis 保存操作");
            return;
        }
        try {
            String key = SESSION_KEY_PREFIX + conversationId;
            redisTemplate.opsForValue().set(key, state, sessionTtl, TimeUnit.SECONDS);
            logger.debug("会话状态已保存到 Redis，会话ID: {}", conversationId);
        } catch (Exception e) {
            logger.error("保存会话状态到 Redis 失败，会话ID: {}", conversationId, e);
        }
    }

    /**
     * 从 Redis 加载 Agent 状态。
     *
     * @param conversationId 会话ID
     * @return AgentState 对象，如果不存在或类型不匹配返回 null
     */
    public AgentState loadState(String conversationId) {
        if (conversationId == null || conversationId.trim().isEmpty()) {
            return null;
        }
        try {
            String key = SESSION_KEY_PREFIX + conversationId;
            Object obj = redisTemplate.opsForValue().get(key);
            if (obj instanceof AgentState) {
                logger.debug("会话状态已从 Redis 加载，会话ID: {}", conversationId);
                return (AgentState) obj;
            }
            return null;
        } catch (Exception e) {
            logger.error("从 Redis 加载会话状态失败，会话ID: {}", conversationId, e);
            return null;
        }
    }

    /**
     * 删除会话状态。
     *
     * @param conversationId 会话ID
     */
    public void deleteState(String conversationId) {
        if (conversationId == null) return;
        try {
            String key = SESSION_KEY_PREFIX + conversationId;
            redisTemplate.delete(key);
            logger.debug("会话状态已从 Redis 删除，会话ID: {}", conversationId);
        } catch (Exception e) {
            logger.error("从 Redis 删除会话状态失败", e);
        }
    }
}