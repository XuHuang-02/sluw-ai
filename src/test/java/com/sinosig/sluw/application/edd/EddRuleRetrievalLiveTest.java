package com.sinosig.sluw.application.edd;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sinosig.sluw.application.client.RagFlowClient;
import com.sinosig.sluw.application.commons.web.ConfigReader;
import com.sinosig.sluw.application.config.AiClientConfig;
import com.sinosig.sluw.application.edd.service.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.core.io.support.ResourcePropertySource;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Explicit opt-in live smoke test; never part of routine network-free verification. */
@EnabledIfSystemProperty(named="edd.live", matches="true")
class EddRuleRetrievalLiveTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final OffsetDateTime FROM = OffsetDateTime.parse("2026-01-01T00:00:00+08:00");
    private static final OffsetDateTime AS_OF = OffsetDateTime.parse("2026-09-20T12:00:00+08:00");

    @Test void retrieveFromRealRagFlow() throws Exception {
        String mode = System.getProperty("edd.live.mode", "capture");
        assertTrue(Set.of("capture", "verify").contains(mode), "Mode must be capture or verify");
        try (var context = new AnnotationConfigApplicationContext()) {
            // Narrow context: real ConfigReader/client/HTTP, no database, Redis or LLM startup.
            context.getEnvironment().getPropertySources().addLast(new ResourcePropertySource(
                    new EncodedResource(new FileSystemResource(System.getProperty(
                            "edd.live.config", "local-settings.properties")), StandardCharsets.UTF_8)));
            context.registerBean(ConfigReader.class);
            context.registerBean("restTemplate", RestTemplate.class,
                    () -> context.getAutowireCapableBeanFactory().createBean(AiClientConfig.class).restTemplate());
            context.registerBean(RagFlowClient.class);
            context.refresh();
            Environment env = context.getEnvironment();
            String dataset = required(env, "application.config.ragFlow.edd.datasetId");
            required(env, "application.config.ragFlow.base.apiUrl");
            required(env, "application.config.ragFlow.base.apiKey");
            String document = required(env, "edd.live.documentId");
            var transport = new EddRagFlowRetriever(context.getBean(RagFlowClient.class), "edd");
            Path output = Path.of("target", "edd-live", mode + "-" + System.currentTimeMillis());
            Files.createDirectories(output);
            long start = System.nanoTime();
            if (mode.equals("capture")) {
                var candidates = transport.retrieve(new EddRuleRetrieval.Query(dataset, List.of(document),
                        "EDD-LIVE", "1", AS_OF, "risk_review", Set.of("CASH-HISTORY")));
                JSON.writerWithDefaultPrettyPrinter().writeValue(output.resolve("candidates.json").toFile(), candidates);
                for (int i = 0; i < candidates.size(); i++) {
                    if (candidates.get(i).content() != null)
                        Files.writeString(output.resolve("candidate-" + i + ".txt"), candidates.get(i).content(), StandardCharsets.UTF_8);
                }
                var report = JSON.createObjectNode().put("mode", mode).put("testOnly", true)
                        .put("candidateCount", candidates.size()).put("elapsedMillis", elapsed(start));
                JSON.writerWithDefaultPrettyPrinter().writeValue(output.resolve("report.json").toFile(), report);
                assertFalse(candidates.isEmpty(), "No candidates; inspect query, parsing and retrieval settings. Output: " + output);
                System.out.println("EDD live capture (not approval): " + output);
                return;
            }
            // Freeze an independently reviewed baseline BEFORE this retrieval; never trust the current response as its own oracle.
            String chunk = required(env, "edd.live.chunkId");
            String baseline = Files.readString(Path.of(required(env, "edd.live.reviewedContentFile")), StandardCharsets.UTF_8);
            assertFalse(baseline.isBlank(), "Reviewed content is empty");
            var pack = EddRulePackage.load("""
                    {"id":"EDD-LIVE","version":"1","approval":"approved","approval_ref":"SYN-LIVE-REVIEW",
                     "approved_by":"SYN-TEST-REVIEWER","approved_at":"2026-01-01T00:00:00+08:00",
                     "valid_from":"2026-01-01T00:00:00+08:00","valid_to":null,"test_only":true,
                     "scope":{"subject_type":"natural_person","triggers":["risk_review"]},
                     "rules":[{"id":"CASH-HISTORY","target":"risk_factor","value":"risk_increasing",
                     "clause_ref":"SYN-LIVE-1","conditions":[{"fact":"cash_status","operator":"eq","expected":"found"}]}]}
                    """);
            var clause = new EddRuleRetrieval.Clause(dataset, document, chunk, "EDD-LIVE", "1", "CASH-HISTORY",
                    "synthetic-v1", "synthetic test document / reviewed chunk", EddRuleRetrieval.hash(baseline),
                    "SYN-LIVE-REVIEW", FROM, FROM, null, Set.of("risk_review"), true, true);
            var result = new EddRuleRetrieval(dataset, transport, List.of(clause), true)
                    .retrieve(pack, AS_OF, "risk_review", Set.of("CASH-HISTORY"));
            var report = JSON.createObjectNode().put("mode", mode).put("elapsedMillis", elapsed(start));
            report.set("result", JSON.valueToTree(result));
            JSON.writerWithDefaultPrettyPrinter().writeValue(output.resolve("report.json").toFile(), report);
            var expected = EddRuleRetrieval.Status.valueOf(env.getProperty("edd.live.expectedStatus", "FOUND"));
            System.out.println("EDD live verify: " + result.status() + "; output: " + output);
            assertEquals(expected, result.status(), "Inspect report.json diagnostics");
            assertTrue(result.testOnly());
            if (expected == EddRuleRetrieval.Status.FOUND) {
                assertEquals(1, result.evidence().size());
                assertEquals(baseline, result.evidence().get(0).content());
                assertEquals("EDD-LIVE@1:CASH-HISTORY", result.evidence().get(0).ruleRef());
            }
        }
    }

    private static String required(Environment env, String key) {
        String value = env.getProperty(key);
        assertNotNull(value, "Missing property: " + key);
        assertFalse(value.isBlank() || value.startsWith("<"), "Configure property: " + key);
        return value;
    }
    private static long elapsed(long start) { return (System.nanoTime() - start) / 1_000_000; }
}
