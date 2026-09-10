package com.sinosig.sluw.application.tools;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sinosig.sluw.application.tools.config.JsonSemanticConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * MCP 工具服务。
 * <p>使用 WebClient 调用外部 MCP Server，并通过 {@link JsonSemanticConverter} 进行响应语义转换。</p>
 *
 * @author SinoSig AI Team
 */
@Service
public class McpToolService {

    private static final Logger logger = LoggerFactory.getLogger(McpToolService.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final WebClient webClient;

    @Autowired
    private JsonSemanticConverter semanticConverter;

    public McpToolService(WebClient.Builder webClientBuilder) {
        // 实际 MCP 服务地址建议从配置文件注入
        this.webClient = webClientBuilder.baseUrl("http://mcp-server.example.com").build();
    }

    /**
     * 查询保险规则条款（调用 MCP 服务）。
     *
     * @param keyword 条款关键词
     * @return 条款内容的语义描述
     */
    @Tool(description = "查询保险规则条款，需要提供条款关键词或编号。返回条款内容、适用场景等。")
    public String queryRuleClause(
            @ToolParam(description = "条款关键词，例如 '重疾险'、'理赔'") String keyword) {

        logger.info("调用 queryRuleClause: keyword={}", keyword);

        if (keyword == null || keyword.trim().isEmpty()) {
            return buildErrorResponse("关键词不能为空");
        }

        try {
            String rawJson = webClient.get()
                    .uri("/rule/clause?keyword={keyword}", keyword)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block(Duration.ofSeconds(5));

            if (rawJson == null) {
                logger.warn("MCP 服务返回空结果，keyword={}", keyword);
                return buildErrorResponse("MCP服务返回空结果");
            }

            logger.info("成功获取条款内容，keyword={}", keyword);
            return semanticConverter.convert("mcp.rule-clause", rawJson);

        } catch (Exception e) {
            logger.error("调用 MCP 服务异常，keyword={}", keyword, e);
            return buildErrorResponse("调用 MCP 服务失败: " + e.getMessage());
        }
    }

    private String buildErrorResponse(String message) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", message);
        try {
            return objectMapper.writeValueAsString(error);
        } catch (JsonProcessingException e) {
            logger.error("JSON序列化失败", e);
            return "{\"error\": \"序列化失败\"}";
        }
    }
}