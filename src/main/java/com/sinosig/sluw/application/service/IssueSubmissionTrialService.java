package com.sinosig.sluw.application.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/** Stateless trial: no memory, advisors, tools, retrieval or business writes. */
@Service
public class IssueSubmissionTrialService {
    private final ChatClient client;
    private final String instructions;
    private final String preparationInstructions;
    static final String PREPARATION_STAGE = "BEFORE_SEND_INTERNAL_AGENCY";
    private final ObjectMapper json = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);

    public IssueSubmissionTrialService(ChatModel model) throws java.io.IOException {
        client = ChatClient.builder(model).build();
        preparationInstructions = new ClassPathResource("prompts/issue-send-preparation.txt")
                .getContentAsString(StandardCharsets.UTF_8);
        instructions = new ClassPathResource("prompts/issue-submission-trial.txt")
                .getContentAsString(StandardCharsets.UTF_8);
    }

    public Mono<String> answer(String input) {
        return Mono.fromCallable(() -> {
            String normalized;
            try { normalized = validate(input); }
            catch (IllegalArgumentException e) { return "【问题件提交试判】\n处理去向：暂无法确定。\n命中的代码条件：未执行，输入格式不合要求。\n引用的输入记录：无。\n缺失信息：" + e.getMessage(); }
            String text = client.prompt(new Prompt(List.of(new SystemMessage(instructionsFor(normalized)),
                    new UserMessage(normalized)))).call().content();
            if (text == null || text.isBlank()) throw new IllegalStateException("empty model response");
            return "【AI流程试判，仅供核对，未执行任何业务操作】\n" + text;
        }).subscribeOn(Schedulers.boundedElastic())
          .timeout(Duration.ofSeconds(50))
          .onErrorReturn("【问题件提交试判】\n处理去向：暂无法确定。\n命中的代码条件：模型调用未完成。\n引用的输入记录：无。\n缺失信息：服务暂不可用，请稍后重新提交完整JSON。");
    }

    private String instructionsFor(String normalized) throws java.io.IOException {
        return PREPARATION_STAGE.equals(json.readTree(normalized).path("experimentStage").asText())
                ? preparationInstructions : instructions;
    }

    String validate(String input) {
        if (input == null || input.isBlank() || input.length() > 60000)
            throw new IllegalArgumentException("请提交不超过60000字符的完整JSON对象。");
        try {
            JsonNode root = json.readTree(input);
            if (!root.isObject() || !root.path("contno").isTextual() || root.path("contno").asText().isBlank())
                throw new IllegalArgumentException("需要JSON对象及非空contno；请使用模板。");
            JsonNode stage = root.get("experimentStage");
            if (stage != null && (!stage.isTextual() ||
                    !(PREPARATION_STAGE.equals(stage.asText()) || "ISSUE_FLOW".equals(stage.asText()))))
                throw new IllegalArgumentException("experimentStage仅支持BEFORE_SEND_INTERNAL_AGENCY或ISSUE_FLOW。");
            JsonNode entry = root.get("entryConfirmed");
            if (entry != null && !entry.isNull() && !entry.isBoolean())
                throw new IllegalArgumentException("entryConfirmed仅允许true、false或null。");
            for (String name : List.of("candidateErrors", "lcissuepol", "currentErrors", "historyErrors", "lwmission", "lbmission", "lwnotepad", "autoallotbyerr")) {
                JsonNode value = root.get(name);
                if (value != null && !value.isNull() && !value.isArray())
                    throw new IllegalArgumentException(name + "必须为数组，未获取时用null。");
            }
            if (!root.path("completeness").isObject())
                throw new IllegalArgumentException("需要completeness说明各组资料是否完整。");
            var flags = root.path("completeness").elements();
            while (flags.hasNext()) {
                JsonNode flag = flags.next();
                if (!flag.isBoolean() && !flag.isNull())
                    throw new IllegalArgumentException("completeness值仅允许true、false或null。");
            }
            for (String name : List.of("candidateErrors", "lcissuepol", "currentErrors", "historyErrors", "lwmission", "lbmission", "lwnotepad")) {
                JsonNode rows = root.get(name);
                if (rows != null && rows.isArray()) for (JsonNode row : rows)
                    if (!row.isObject()) throw new IllegalArgumentException(name + "中的记录必须为对象。");
            }
            return json.writeValueAsString(root);
        } catch (IllegalArgumentException e) { throw e; }
        catch (Exception e) { throw new IllegalArgumentException("JSON无法解析，检查格式、重复字段和多余内容。"); }
    }
}
