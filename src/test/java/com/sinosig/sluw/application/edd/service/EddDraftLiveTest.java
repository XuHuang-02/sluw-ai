package com.sinosig.sluw.application.edd.service;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.sinosig.sluw.application.config.AiClientConfig;
import com.sinosig.sluw.application.service.routing.RoutingModelFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.boot.*;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Explicit real-model evaluation of synthetic fixtures; no database, Redis, RAG or customer lookup. */
@EnabledIfSystemProperty(named="edd.llm.live",matches="true")
class EddDraftLiveTest {
    @Configuration(proxyBeanMethods=false)
    @Import(AiClientConfig.class)
    @ImportAutoConfiguration({
        org.springframework.ai.model.deepseek.autoconfigure.DeepSeekChatAutoConfiguration.class,
        com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeChatAutoConfiguration.class,
        org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration.class,
        org.springframework.ai.retry.autoconfigure.SpringAiRetryAutoConfiguration.class,
        org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration.class,
        org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration.class,
        org.springframework.boot.autoconfigure.web.client.RestClientAutoConfiguration.class,
        org.springframework.boot.autoconfigure.web.reactive.function.client.WebClientAutoConfiguration.class
    })
    static class ModelConfiguration {}

    static ConfigurableApplicationContext open(String... arguments) {
        // Standard Boot profile/config import, but no component scan of business services.
        return new SpringApplicationBuilder(ModelConfiguration.class).web(WebApplicationType.NONE)
                .bannerMode(Banner.Mode.OFF).logStartupInfo(false).run(arguments);
    }
    static ObjectNode fixture(String id) throws Exception {
        String file=switch(id) {
            case "SYN-V1-016" -> "fact-scope-cases.json";
            case "SYN-V1-017","SYN-V1-021" -> "rule-extra-cases.json";
            case "SYN-V1-018" -> "history-cases.json";
            default -> throw new IllegalArgumentException("Supported cases: SYN-V1-016,017,018,021");
        };
        var mapper=new ObjectMapper();
        for(JsonNode c:mapper.readTree(Files.readString(Path.of("tools/edd/fixtures/"+file))).path("cases")) {
            if(c.path("case_id").asText().equals(id.equals("SYN-V1-018")?"SYN-V1-003":id)) {
                ObjectNode request=(ObjectNode)c.path("input").deepCopy();
                if(id.equals("SYN-V1-018")) {
                    ObjectNode template=(ObjectNode)request.path("events").get(0).deepCopy();
                    ArrayNode events=request.putArray("events");
                    for(int i=0;i<10000;i++) {
                        String ref="LONG-"+i;
                        events.add(template.deepCopy().put("fact_id",ref).put("source_record_id",ref)
                                .put("amount","10.00").put("payment_method",i==9999?"cash":"bank_transfer"));
                    }
                    request.put("snapshot_id","SYN-V1-018-LIVE-INPUT-1");
                }
                assertTrue(request.path("synthetic").asBoolean());return request;
            }
        }
        throw new IllegalArgumentException("Synthetic fixture unavailable");
    }
    @Test void generateFromConfiguredRealModel() throws Exception {
        var mapper=new ObjectMapper();
        List<String> cases=Arrays.stream(System.getProperty("edd.llm.cases","SYN-V1-016").split(",")).map(String::trim).toList();
        // Check fixture selection before initializing providers.
        List<ObjectNode> requests=new ArrayList<>();for(String id:cases)requests.add(fixture(id));
        try(var context=open()) {
            var env=context.getEnvironment();
            String version=env.getRequiredProperty("edd.llm.model-config-version");
            var settings=new EddDraftService.Settings(
                env.getProperty("edd.llm.max-context-bytes",Integer.class,96000),
                env.getProperty("edd.llm.max-response-bytes",Integer.class,64000),
                env.getProperty("edd.llm.max-output-tokens",Integer.class,4096),
                Duration.ofMillis(env.getProperty("edd.llm.timeout-ms",Long.class,45000L)),1,1,version,
                EddDraftService.OutputMode.valueOf(env.getProperty("edd.llm.output-mode","SCHEMA_PROMPT")));
            Path dir=Path.of("target/edd-llm-live/"+System.currentTimeMillis());Files.createDirectories(dir);
            ArrayNode summaries=mapper.createArrayNode();
            try(var service=new EddDraftService(context.getBean(ChatModel.class),new RoutingModelFactory(),settings)) {
                for(int i=0;i<cases.size();i++) {
                    var input=requests.get(i);
                    var evaluation=new EddRuleService().evaluate(new EddHistoryService().analyze(new EddFactService().prepare(input)));
                    var retrieval=new EddRuleRetrieval.Result(EddRuleRetrieval.Status.RULES_MISSING,false,List.of(),List.of(),List.of("LIVE_TEST_NO_APPROVED_RULES"));
                    var result=service.analyze(evaluation,retrieval);
                    mapper.writerWithDefaultPrettyPrinter().writeValue(dir.resolve(cases.get(i)+".json").toFile(),result);
                    summaries.addObject().put("case_id",cases.get(i)).put("status",result.status().name())
                            .put("elapsed_ms",result.metadata().path("elapsed_ms").asLong());
                }
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(dir.resolve("summary.json").toFile(),summaries);
            System.out.println("EDD real model results (require human review): "+dir);
            for(JsonNode row:summaries)assertTrue(Set.of("COMPLETED","COMPLETED_WITH_GAPS").contains(row.path("status").asText()),"Inspect case report: "+row.path("case_id").asText());
        }
    }
}
