package com.sinosig.sluw.application.service;

import com.sinosig.sluw.application.dto.AgentState;
import com.sinosig.sluw.application.dto.ExcelTest;
import com.sinosig.sluw.application.dto.IntentType;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Service  // ✅ 改为 @Service
public class ExcelTestService {

    private static final Logger log = LoggerFactory.getLogger(ExcelTestService.class);

    private final String filePath = "/data/home/ganchenchen/工作簿11.xlsx";
    private final ChatClient chatClient;

    public ExcelTestService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
    }

    /**
     * 处理本地 Excel 文件
     * @return 校验结果
     */
    public String processLocalFile() {
        try {
            log.info("文件是否存在: {}", new File(filePath).exists());
            List<ExcelTest> ruleList = parseExcelFileFromPath(filePath);
            if (ruleList.isEmpty()) {
                return "表格为空，无数据可处理";
            }

            String tableContent = convertToMarkdownTable(ruleList);
            log.info("生成的Markdown表格内容预览：\n{}", tableContent);

            String promptTemplate = """
                    你是一个监管规则审核专家。请对以下表格中的'规则标签'列进行校验：
                    1. 根据“表中文名、数据项名称、规则说明”，校验规则是否重复，是否冗余。
                    2. 对于有问题的行，请指出规则id和具体问题。

                    表格内容如下：
                    {table_content}

                    请以JSON格式返回结果，格式如下：
                {
                  "duplicate_rows": [规则ID列表],
                  "invalid_rows": [
                    {"row_number": 规则ID, "reason": "问题原因"}
                  ]
                }
                """;

//            PromptTemplate template = new PromptTemplate(promptTemplate);
//            Prompt prompt = template.create(Map.of("table_content", tableContent));
//            log.info("最终发送给模型的Prompt：\n{}", prompt.getContents());
            // 手动替换
            String promptContent = promptTemplate.replace("{table_content}", tableContent);
            log.info("最终Prompt：\n{}", promptContent);

            String fullResult = chatClient.prompt(promptContent)
                    .call()
                    .content();
            log.info("模型返回结果：{}", fullResult);
            return fullResult;
        } catch (Exception e) {
            log.error("文件处理失败", e);
            return "文件处理失败：" + e.getMessage();
        }
    }
//    public String generateSync(AgentState agentState) {
//        String preGenerated = getPreGeneratedResponse(agentState);
//        if (preGenerated != null) {
//            logger.info("命中预生成回复（意图：{}），跳过 LLM 调用", agentState.getIntentType());
//            agentState.setResponse(preGenerated);
//            return preGenerated;
//        }
//
//        boolean useHistory = agentState.isUseRefinerMemory();
//        String prompt = templateConfig.buildResponseGeneratorPrompt(agentState, useHistory);
//        logger.debug("调用 LLM 生成最终回复，意图类型：{}", agentState.getIntentType());
//
//        ChatResponse response;
//        try {
//            response = chatClient.prompt(prompt).call().chatResponse();
//        } catch (Exception e) {
//            logger.error("LLM 同步生成失败: {}", e.getMessage());
//            throw new RuntimeException("模型繁忙，请稍后重试！", e);
//        }
//
//        if (response == null || response.getResult() == null) {
//            throw new RuntimeException("模型返回null或无效响应");
//        }
//
//        String content = response.getResult().getOutput().getText();
//        if (StringUtils.isBlank(content)) {
//            throw new RuntimeException("模型返回内容为空");
//        }
//
//        Usage usage = response.getMetadata() != null ? response.getMetadata().getUsage() : null;
//        if (usage != null) {
//            agentState.accumulateTokenUsage(usage);
//            logger.debug("同步生成 Token 用量 - 输入: {}, 输出: {}, 总计: {}",
//                    usage.getPromptTokens(), usage.getCompletionTokens(), usage.getTotalTokens());
//        }
//
//        agentState.setResponse(content);
//        logger.info("LLM 生成回复成功，长度：{}", content.length());
//        return content;
//    }

    private String getPreGeneratedResponse(AgentState agentState) {
        Map<String, Object> context = agentState.getContext();
        if (context == null) return null;

        if (agentState.getIntentType() == IntentType.TOOL_EXECUTION && context.containsKey("tool_execution_result")) {
            return (String) context.get("tool_execution_result");
        }

        if (agentState.getIntentType() == IntentType.CLARIFICATION && context.containsKey("clarification_response")) {
            return (String) context.get("clarification_response");
        }

        return null;
    }
    /**
     * 从本地文件路径解析 Excel
     */
    private List<ExcelTest> parseExcelFileFromPath(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        if (!path.toFile().exists()) {
            throw new IllegalArgumentException("文件不存在: " + filePath);
        }
        try (FileInputStream fis = new FileInputStream(path.toFile());
             Workbook workbook = new XSSFWorkbook(fis)) {

            List<ExcelTest> ruleList = new ArrayList<>();
            Sheet sheet = workbook.getSheetAt(0);
            // 假设第一行为表头，从第二行开始读取
            int rowStart = sheet.getFirstRowNum() + 1;
            int rowEnd = sheet.getLastRowNum();
            for (int rowIndex = rowStart; rowIndex <= rowEnd; rowIndex++) {
                Row row = sheet.getRow(rowIndex);
                if (row == null) continue; // 跳过空行

                ExcelTest rule = new ExcelTest();
                // ⚠️ 根据你 Excel 的实际列顺序调整索引（从0开始）
                // 假设顺序：0-规则ID, 1-规则类型名称, 2-表中文名, 3-数据项代码, 4-数据项名称, 5-规则编码, 6-规则说明, 7-规则特征
                rule.setRuleID(getCellValueAsString(row.getCell(0)));
                rule.setRuleTypeName(getCellValueAsString(row.getCell(1)));
                rule.setTable(getCellValueAsString(row.getCell(2)));
                rule.setDataSingle(getCellValueAsString(row.getCell(3)));
                rule.setDataName(getCellValueAsString(row.getCell(4)));
                rule.setRuleCode(getCellValueAsString(row.getCell(5)));
                rule.setRuleDescription(getCellValueAsString(row.getCell(6)));
                rule.setRuleTZ(getCellValueAsString(row.getCell(7)));
                // 如果有更多字段，继续添加

                ruleList.add(rule);
            }
            log.info("解析到 {} 行数据", ruleList.size());
            return ruleList;
        }
    }

    private String getCellValueAsString(Cell cell) {
        if (cell == null) return "";
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf(cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            default -> "";
        };
    }

    private String convertToMarkdownTable(List<ExcelTest> ruleList) {
        if (ruleList == null || ruleList.isEmpty()) return "（表格为空）";

        StringBuilder sb = new StringBuilder();
        sb.append("| 行号 | 规则ID | 规则类型名称| 表中文名 | 数据项代码 | 数据项名称 | 规则编码 | 规则说明 | 规则特征 |\n");
        sb.append("|------|------|----------|----------|----------|----------|----------|----------|----------|\n");

        for (int i = 0; i < ruleList.size(); i++) {
            ExcelTest rule = ruleList.get(i);
            int rowNumber = i + 1;
            sb.append("| ")
                    .append(rowNumber).append(" | ")
                    .append(rule.getRuleID()).append(" | ")
                    .append(rule.getRuleTypeName()).append(" | ")
                    .append(rule.getTable()).append(" | ")
                    .append(rule.getDataSingle()).append(" | ")
                    .append(rule.getDataName()).append(" | ")
                    .append(rule.getRuleCode()).append(" | ")
                    .append(rule.getRuleDescription()).append(" | ")
                    .append(rule.getRuleTZ()).append(" |\n");
        }
        return sb.toString();
    }
}