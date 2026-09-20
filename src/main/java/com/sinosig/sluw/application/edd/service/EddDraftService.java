package com.sinosig.sluw.application.edd.service;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import com.sinosig.sluw.application.service.routing.RoutingModelFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.*;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.*;
import org.springframework.core.io.ClassPathResource;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/** Generates an unvalidated draft only. Task08 owns semantic validation and publication gating. */
public final class EddDraftService implements AutoCloseable {
    public static final String PROMPT_VERSION="edd-draft-v1";
    public static final List<String> SECTIONS=List.of("customer_and_transactions","risk_grade_basis",
            "historical_cash","suspicious_reports","blacklist_and_external","high_risk_scenarios");
    public enum Status { DRAFT, CONTEXT_LIMIT, INVALID_OUTPUT, MODEL_ERROR, TIMEOUT, BUSY, INTERRUPTED }
    public record Settings(int maxContextBytes,int maxResponseBytes,int maxOutputTokens,Duration timeout,
                           int workers,int queueCapacity,String modelConfigVersion) {
        public Settings {
            if(maxContextBytes<1||maxResponseBytes<1||maxOutputTokens<1||timeout==null||timeout.isNegative()
                    ||timeout.isZero()||workers<1||queueCapacity<1||modelConfigVersion==null||modelConfigVersion.isBlank())
                throw new IllegalArgumentException("Invalid EDD draft settings");
        }
        public static Settings defaults(String modelConfigVersion) {
            return new Settings(96000,64000,4096,Duration.ofSeconds(45),4,16,modelConfigVersion);
        }
    }
    public record Result(Status status,ObjectNode draft,ObjectNode facts,ObjectNode metadata) {
        public Result {draft=draft==null?null:draft.deepCopy();facts=facts.deepCopy();metadata=metadata.deepCopy();}
        @Override public ObjectNode draft(){return draft==null?null:draft.deepCopy();}
        @Override public ObjectNode facts(){return facts.deepCopy();}
        @Override public ObjectNode metadata(){return metadata.deepCopy();}
    }
    private static final ObjectMapper JSON=new ObjectMapper().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final Settings settings;
    private final Supplier<ChatModel> modelSupplier;
    private final ThreadPoolExecutor executor;
    private final String systemPrompt;
    private volatile ChatClient client;

    /** Shares existing configured API/transport, model isolation, no tool/advisor/history registration. */
    public EddDraftService(ChatModel configuredModel,RoutingModelFactory factory,Settings settings) {
        this(()->factory.isolate(configuredModel),settings);
    }
    // Test seam uses a fake ChatModel; production callers use the configured-model constructor.
    EddDraftService(Supplier<ChatModel> supplier,Settings settings) {
        this.settings=Objects.requireNonNull(settings);this.modelSupplier=Objects.requireNonNull(supplier);
        try(var input=new ClassPathResource("edd/prompts/draft-v1.txt").getInputStream()) {
            systemPrompt=new String(input.readAllBytes(),StandardCharsets.UTF_8);
        } catch(IOException e){throw new IllegalStateException("EDD prompt unavailable");}
        executor=new ThreadPoolExecutor(settings.workers(),settings.workers(),0,TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(settings.queueCapacity()),r->{Thread t=new Thread(r,"edd-draft");t.setDaemon(true);return t;},
                new ThreadPoolExecutor.AbortPolicy());
    }
    private synchronized ChatClient client() {
        if(client==null)client=ChatClient.builder(modelSupplier.get()).build();
        return client;
    }
    public Result generate(EddRuleService.Evaluation evaluation,EddRuleRetrieval.Result retrieval) {
        EddDraftContext.Packed packed=new EddDraftContext().pack(evaluation,retrieval);
        String payload=packed.data().toString();
        int bytes=systemPrompt.getBytes(StandardCharsets.UTF_8).length+payload.getBytes(StandardCharsets.UTF_8).length;
        ObjectNode metadata=JSON.createObjectNode().put("input_hash",packed.inputHash())
                .put("input_version",evaluation.history().prepared().input().request().path("snapshot_id").asText())
                .put("prompt_version",PROMPT_VERSION).put("prompt_hash",EddRuleRetrieval.hash(systemPrompt))
                .put("context_hash",EddRuleRetrieval.hash(payload)).put("context_bytes",bytes)
                .put("model_config_version",settings.modelConfigVersion()).put("model",(String)null)
                .put("requires_validation",true).put("execution_permitted",false);
        metadata.set("rule_version",evaluation.result().path("rule_package").deepCopy());
        if(bytes>settings.maxContextBytes())return result(Status.CONTEXT_LIMIT,null,packed,metadata,0);
        long start=System.nanoTime();
        Future<ChatResponse> pending;
        try {
            pending=executor.submit(()->client().prompt(new Prompt(List.of(new SystemMessage(systemPrompt),new UserMessage(payload)),
                    ChatOptions.builder().maxTokens(settings.maxOutputTokens()).build())).call().chatResponse());
        } catch(RejectedExecutionException e){return result(Status.BUSY,null,packed,metadata,start);}
        try {
            ChatResponse response=pending.get(settings.timeout().toMillis(),TimeUnit.MILLISECONDS);
            if(response==null||response.getResult()==null||response.getResult().getOutput()==null)
                return result(Status.INVALID_OUTPUT,null,packed,metadata,start);
            if(response.getMetadata()!=null)metadata.put("model",response.getMetadata().getModel());
            var message=response.getResult().getOutput();
            if(message.hasToolCalls())return result(Status.INVALID_OUTPUT,null,packed,metadata,start);
            String text=message.getText();
            if(text==null||text.getBytes(StandardCharsets.UTF_8).length>settings.maxResponseBytes())
                return result(Status.INVALID_OUTPUT,null,packed,metadata,start);
            ObjectNode draft=parse(text,packed);
            return result(Status.DRAFT,draft,packed,metadata,start);
        } catch(TimeoutException e) {
            pending.cancel(true);executor.purge();return result(Status.TIMEOUT,null,packed,metadata,start);
        } catch(InterruptedException e) {
            pending.cancel(true);executor.purge();Thread.currentThread().interrupt();return result(Status.INTERRUPTED,null,packed,metadata,start);
        } catch(ExecutionException e) {
            return result(Status.MODEL_ERROR,null,packed,metadata,start);
        } catch(IOException|IllegalArgumentException e) {
            return result(Status.INVALID_OUTPUT,null,packed,metadata,start);
        }
    }
    private static Result result(Status status,ObjectNode draft,EddDraftContext.Packed packed,ObjectNode metadata,long start) {
        metadata.put("elapsed_ms",start==0?0:(System.nanoTime()-start)/1_000_000);
        return new Result(status,draft,packed.fixed(),metadata);
    }
    private static ObjectNode parse(String text,EddDraftContext.Packed packed) throws IOException {
        JsonNode parsed=JSON.readTree(text);
        fields(parsed,Set.of("overview_sections","disposition_recommendations","missing_items"));
        ObjectNode draft=(ObjectNode)parsed;
        JsonNode sections=draft.path("overview_sections");
        if(!sections.isArray()||sections.size()!=6)throw invalid();
        Set<String> seen=new HashSet<>();
        for(JsonNode section:sections) {
            fields(section,Set.of("section","text","fact_refs","rule_refs"));
            String name=string(section,"section");
            if(!SECTIONS.contains(name)||!seen.add(name))throw invalid();
            string(section,"text");references((ObjectNode)section,packed);
        }
        JsonNode recommendations=draft.path("disposition_recommendations");
        if(!recommendations.isArray()||recommendations.size()>100)throw invalid();
        for(JsonNode recommendation:recommendations) {
            fields(recommendation,Set.of("status","value","reason","fact_refs","rule_refs"));
            String status=string(recommendation,"status");
            if(!Set.of("suggestion","insufficient_rules","insufficient_evidence","requires_institution_review","not_applicable").contains(status))throw invalid();
            if(status.equals("suggestion"))string(recommendation,"value");else if(!recommendation.path("value").isNull())throw invalid();
            string(recommendation,"reason");references((ObjectNode)recommendation,packed);
        }
        JsonNode missing=draft.path("missing_items");
        if(!missing.isArray()||missing.size()>1000)throw invalid();
        for(JsonNode issue:missing) {
            fields(issue,Set.of("code","path","message"));string(issue,"code");string(issue,"message");
            if(!issue.path("path").isTextual())throw invalid();
        }
        ArrayNode merged=packed.fixed().withArray("missing_items");
        for(JsonNode issue:missing)if(!contains(merged,issue))merged.add(issue);
        draft.set("missing_items",merged);
        // Model cannot change these values: they are never part of the generation output contract.
        for(String key:List.of("grade_recommendation","report_recommendation","proposed_grade","current_risk_grade"))
            draft.set(key,packed.fixed().path(key));
        return draft;
    }
    private static boolean contains(ArrayNode nodes,JsonNode value) {for(JsonNode node:nodes)if(node.equals(value))return true;return false;}
    private static void references(ObjectNode item,EddDraftContext.Packed packed) {
        for(String key:List.of("fact_refs","rule_refs")) {
            JsonNode refs=item.path(key);if(!refs.isArray()||refs.size()>100000)throw invalid();
            LinkedHashSet<String> expanded=new LinkedHashSet<>();
            for(JsonNode ref:refs) {
                if(!ref.isTextual())throw invalid();
                if(key.equals("rule_refs")) {
                    if(!packed.ruleRefs().contains(ref.asText()))throw invalid();expanded.add(ref.asText());
                } else {
                    List<String> originals=packed.citations().get(ref.asText());
                    if(originals==null)throw invalid();expanded.addAll(originals);
                }
            }
            if(expanded.size()>100000)throw invalid();
            ArrayNode out=item.putArray(key);expanded.forEach(out::add);
        }
    }
    private static void fields(JsonNode node,Set<String> expected) {
        if(node==null||!node.isObject())throw invalid();
        Set<String> actual=new HashSet<>();node.fieldNames().forEachRemaining(actual::add);
        if(!actual.equals(expected))throw invalid();
    }
    private static String string(JsonNode node,String field) {
        JsonNode value=node.path(field);
        if(!value.isTextual()||value.asText().isBlank()||value.asText().length()>20000)throw invalid();
        return value.asText();
    }
    private static IllegalArgumentException invalid(){return new IllegalArgumentException("Invalid EDD draft output");}
    @Override public void close(){executor.shutdownNow();}
}
