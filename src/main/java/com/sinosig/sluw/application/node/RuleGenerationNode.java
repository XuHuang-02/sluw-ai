package com.sinosig.sluw.application.node;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 合规检核规则生成节点。
 * <p>两阶段流程：需求解析（提取字段清单）→ 精确检索（知识库）→ 规则表格生成。</p>
 * <p>异常处理：任一步骤失败将降级输出友好错误提示，不影响主流程。</p>
 */
@Component
class RuleGenerationNode {

    private static final Pattern JSON_PATTERN = Pattern.compile("```(?:json)?\\s*(.*?)\\s*```", Pattern.DOTALL);

    // 第一阶段：需求解析提示词
//    private static final String STAGE1_PARSE_PROMPT = """
//            # Role
//            你是一个需求解析器。主人希望生成合规检核规则，请从问题中提取关键信息。
//
//            # 输出格式（严格 JSON）
//            {
//              "theme": "主题域（如年金保险）",
//              "tables": ["推测可能涉及的表名", "险种定义表"],
//              "fields": ["推测可能涉及的字段代码", "BXQJ", "DDQTS"],
//              "code_sets": ["可能需要码值的字段", "XL"],
//              "need_more_info": false
//            }
//
//            # 规则
//            - 只使用问题中的信息，不要编造。
//            - 如果信息太少，将 need_more_info 设为 true。
//
//            # 用户问题
//            %s
//            """;
//
//    // 第二阶段：生成规则表格提示词
//    private static final String STAGE2_GENERATE_PROMPT = """
//            # Role
//            你是 ICAC 合规助手。请基于以下检索结果生成合规检核规则。
//
//            # 可用字段定义（来自附件1-3）
//            %s
//
//            # 可用码值（来自附件1-4）
//            %s
//
//            # 已有基础校验规则编码（避免重复）
//            %s
//
//            # 用户原始需求
//            %s
//
//            # 输出要求
//            1. 只使用上述字段定义中出现的字段。
//            2. 输出 Markdown 表格，列顺序如下：
//               | 序号 | 主题域 | 表名 | 数据项名称 | 数据项代码 | IF | THEN | 执行方式 | 监管依据 | 监管文件条款原文 | 来源访问地址 | 规则说明 |
//            3. 执行方式从「自动化/关键词匹配/跨表关联/人工核验」选择。
//            4. 如果没有足够信息，输出：“信息不足，无法生成规则。”
//
//            # 开始输出
//            """;
//
//    private final ChatClient chatClient;
//    private final ObjectMapper objectMapper;
//    private final KnowledgeService knowledgeBase; // 知识库检索服务，需注入实际实现
//
//    public RuleGenerationNode(ChatClient chatClient,
//                              ObjectMapper objectMapper,
//                              KnowledgeService knowledgeBase) {
//        this.chatClient = chatClient;
//        this.objectMapper = objectMapper;
//        this.knowledgeBase = knowledgeBase;
//    }
//
//    @Override
//    protected Map<String, Object> doProcess(OverAllState state) {
//        logger.debug("=== 规则生成节点开始 ===");
//        AgentState agentState = extractAgentState(state);
//
//        String userInput = agentState.getUserInput();
//        if (StringUtils.isBlank(userInput)) {
//            logger.warn("用户输入为空，无法生成规则");
//            agentState.setFinalAnswer("主人，请提供需要生成规则的主题域或具体字段。");
//            return buildUpdateMap(agentState);
//        }
//
//        // 1. 第一阶段：需求解析
//        JsonNode requirement = parseRequirement(userInput);
//        if (requirement == null) {
//            agentState.setFinalAnswer("规则需求解析失败，请稍后重试。");
//            return buildUpdateMap(agentState);
//        }
//
//        // 检查信息是否充足
//        if (requirement.has("need_more_info") && requirement.get("need_more_info").asBoolean()) {
//            String msg = "主人，请提供更明确的主题域或字段（例如：年金保险、保险期间 BXQJ）。";
//            agentState.setFinalAnswer(msg);
//            return buildUpdateMap(agentState);
//        }
//
//        // 2. 精确检索
//        RetrievedData retrieved = preciseRetrieval(requirement);
//        if (retrieved.fields.isEmpty()) {
//            String msg = "ICAC 在知识库中未找到相关字段定义，请检查附件1-3是否包含您提到的表或字段。";
//            agentState.setFinalAnswer(msg);
//            return buildUpdateMap(agentState);
//        }
//
//        // 3. 第二阶段：生成规则表格
//        String rulesTable = generateRulesTable(userInput, retrieved);
//        if (StringUtils.isBlank(rulesTable)) {
//            agentState.setFinalAnswer("规则生成失败，请稍后重试。");
//        } else {
//            agentState.setFinalAnswer(rulesTable);
//        }
//
//        return buildUpdateMap(agentState);
//    }
//
//    /**
//     * 第一阶段：调用 LLM 解析需求，返回 JSON 节点。
//     */
//    private JsonNode parseRequirement(String userInput) {
//        String prompt = String.format(STAGE1_PARSE_PROMPT, userInput);
//        logger.debug("调用 LLM 进行需求解析");
//        try {
//            ChatResponse response = chatClient.prompt(prompt).call().chatResponse();
//            if (response == null || response.getResult() == null) {
//                logger.warn("需求解析 LLM 响应为空");
//                return null;
//            }
//            String content = response.getResult().getOutput().getText();
//            if (StringUtils.isBlank(content)) {
//                logger.warn("需求解析返回内容为空");
//                return null;
//            }
//            // 统计 token
//            Usage usage = response.getMetadata() != null ? response.getMetadata().getUsage() : null;
//            if (usage != null) {
//                // 这里无法直接获取 AgentState，但可以在外层统一统计，暂不处理
//                logger.debug("需求解析 Token 用量 - 输入: {}, 输出: {}", usage.getPromptTokens(), usage.getCompletionTokens());
//            }
//            // 提取 JSON（支持 markdown 代码块）
//            String jsonContent = extractJson(content);
//            return objectMapper.readTree(jsonContent);
//        } catch (Exception e) {
//            logger.error("需求解析异常: {}", e.getMessage(), e);
//            return null;
//        }
//    }
//
//    /**
//     * 精确检索：根据解析结果从知识库获取字段定义、码值、已有规则。
//     */
//    private RetrievedData preciseRetrieval(JsonNode requirement) {
//        RetrievedData data = new RetrievedData();
//        List<String> tables = new ArrayList<>();
//        List<String> fields = new ArrayList<>();
//        List<String> codeSets = new ArrayList<>();
//
//        if (requirement.has("tables")) {
//            requirement.get("tables").forEach(node -> tables.add(node.asText()));
//        }
//        if (requirement.has("fields")) {
//            requirement.get("fields").forEach(node -> fields.add(node.asText()));
//        }
//        if (requirement.has("code_sets")) {
//            requirement.get("code_sets").forEach(node -> codeSets.add(node.asText()));
//        }
//
//        int maxFields = 20; // 防止上下文过大
//        for (String table : tables) {
//            if (fields.isEmpty()) {
//                // 未指定字段，获取表的所有字段（限制数量）
//                List<String> allFields = knowledgeBase.queryAllFields(table, maxFields);
//                data.fields.addAll(allFields);
//            } else {
//                for (String field : fields) {
//                    String definition = knowledgeBase.queryFieldDefinition(table, field);
//                    if (definition != null) {
//                        data.fields.add(definition);
//                        if (data.fields.size() >= maxFields) break;
//                    }
//                }
//            }
//            if (data.fields.size() >= maxFields) break;
//        }
//
//        // 查询码值
//        for (String codeSet : codeSets) {
//            Map<String, String> codes = knowledgeBase.queryCodeValues(codeSet);
//            data.codeValues.put(codeSet, codes);
//        }
//
//        // 查询已有基础校验规则（附件1-5）
//        data.existingRules = knowledgeBase.queryExistingBaseRules();
//        return data;
//    }
//
//    /**
//     * 第二阶段：调用 LLM 生成规则表格。
//     */
//    private String generateRulesTable(String userInput, RetrievedData retrieved) {
//        String fieldsText = formatFields(retrieved.fields);
//        String codesText = formatCodeValues(retrieved.codeValues);
//        String existingRulesText = String.join(", ", retrieved.existingRules);
//
//        String prompt = String.format(STAGE2_GENERATE_PROMPT,
//                fieldsText, codesText, existingRulesText, userInput);
//        logger.debug("调用 LLM 生成规则表格");
//        try {
//            ChatResponse response = chatClient.prompt(prompt).call().chatResponse();
//            if (response == null || response.getResult() == null) {
//                logger.warn("规则生成 LLM 响应为空");
//                return null;
//            }
//            String content = response.getResult().getOutput().getText();
//            Usage usage = response.getMetadata() != null ? response.getMetadata().getUsage() : null;
//            if (usage != null) {
//                logger.debug("规则生成 Token 用量 - 输入: {}, 输出: {}", usage.getPromptTokens(), usage.getCompletionTokens());
//            }
//            return content;
//        } catch (Exception e) {
//            logger.error("规则生成异常: {}", e.getMessage(), e);
//            return null;
//        }
//    }
//
//    // ---------- 辅助方法 ----------
//    private String extractJson(String content) {
//        if (StringUtils.isBlank(content)) return "{}";
//        String trimmed = content.trim();
//        Matcher matcher = JSON_PATTERN.matcher(content);
//        if (matcher.find()) {
//            return matcher.group(1).trim();
//        }
//        return trimmed;
//    }
//
//    private String formatFields(List<String> fields) {
//        if (fields == null || fields.isEmpty()) return "无";
//        StringBuilder sb = new StringBuilder();
//        for (String f : fields) {
//            sb.append("- ").append(f).append("\n");
//        }
//        return sb.toString();
//    }
//
//    private String formatCodeValues(Map<String, Map<String, String>> codeValues) {
//        if (codeValues == null || codeValues.isEmpty()) return "无";
//        StringBuilder sb = new StringBuilder();
//        for (Map.Entry<String, Map<String, String>> entry : codeValues.entrySet()) {
//            sb.append(entry.getKey()).append(":\n");
//            for (Map.Entry<String, String> kv : entry.getValue().entrySet()) {
//                sb.append("  ").append(kv.getKey()).append(" : ").append(kv.getValue()).append("\n");
//            }
//        }
//        return sb.toString();
//    }
//
//    private Map<String, Object> buildUpdateMap(AgentState agentState) {
//        Map<String, Object> updateMap = new HashMap<>();
//        updateMap.put("agent_state", agentState);
//        return updateMap;
//    }
//
//    /**
//     * 检索结果内部类
//     */
//    private static class RetrievedData {
//        List<String> fields = new ArrayList<>();
//        Map<String, Map<String, String>> codeValues = new HashMap<>();
//        List<String> existingRules = new ArrayList<>();
//    }
}