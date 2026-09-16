package com.sinosig.sluw.application.service;

import com.sinosig.sluw.application.service.routing.*;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.messages.*;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

/** Isolated per-request trial: no history, RAG, automatic retries or business execution. */
@Service
public class IssueSubmissionTrialService {
    private static final Logger LOG=LoggerFactory.getLogger(IssueSubmissionTrialService.class);
    // Response waiting budget, not a transport cancellation guarantee or an observed service SLA.
    private static final Duration DEFAULT_RESPONSE_TIMEOUT=Duration.ofSeconds(50);
    // Replace with a priced-usage service if experiment billing is introduced.
    private static final String COST_NOTICE="费用：当前实验未启用计价。";
    private volatile ChatClient client;
    private volatile RoutingPolicy policy;
    private final Supplier<ChatModel> modelSupplier;
    private final PolicyLoader policyLoader;
    private final Function<RoutingInput,RoutingTypes.Facts> precalculator;
    private final Duration responseTimeout;
    @FunctionalInterface interface PolicyLoader { RoutingPolicy load() throws Exception; }
    private enum Stage { INPUT, POLICY_INIT, PRECALCULATION, MODEL_ISOLATION, PROMPT, MODEL_CALL, METADATA, BODY, VALIDATION, REPORT }
    private enum Delivery { WAITING, DELIVERED, TIMEOUT, CANCELLED }
    private static final class Invocation {
        final String id=UUID.randomUUID().toString();
        final long start=System.nanoTime();
        final AtomicReference<Delivery> delivery=new AtomicReference<>(Delivery.WAITING);
        volatile Stage stage=Stage.INPUT;
        volatile String promptHash;
        volatile String modelId;
        volatile Integer tokens;
        volatile Long callMs;
    }
    private record Result(String text,String outcome,String reason,String causeType) {}

    @org.springframework.beans.factory.annotation.Autowired
    public IssueSubmissionTrialService(ChatModel model, RoutingModelFactory factory,
        @org.springframework.beans.factory.annotation.Value("${issue-submission-trial.response-timeout-ms:50000}") long timeoutMs) {
        this(()->factory.isolate(model),RoutingPolicy::new,input->new RoutingPrecalculator(input).calculate(),Duration.ofMillis(timeoutMs));
    }
    IssueSubmissionTrialService(ChatModel model) {
        this(()->model,RoutingPolicy::new,input->new RoutingPrecalculator(input).calculate(),DEFAULT_RESPONSE_TIMEOUT);
    }
    IssueSubmissionTrialService(Supplier<ChatModel> modelSupplier,PolicyLoader policyLoader,
        Function<RoutingInput,RoutingTypes.Facts> precalculator,Duration responseTimeout) {
        if(responseTimeout==null||responseTimeout.isNegative()||responseTimeout.isZero())throw new IllegalArgumentException("选路响应等待时间必须大于0。");
        this.modelSupplier=modelSupplier;this.policyLoader=policyLoader;this.precalculator=precalculator;this.responseTimeout=responseTimeout;
    }
    private synchronized ChatClient client() {
        if(client==null)client=ChatClient.builder(modelSupplier.get()).build();
        return client;
    }
    private synchronized RoutingPolicy policy() throws Exception {
        if(policy==null)policy=policyLoader.load();
        if(policy==null)throw new IllegalStateException("policy loader returned null");
        return policy;
    }
    private static String type(Throwable error) { return error==null?"none":error.getClass().getSimpleName(); }
    private static long elapsed(long start) { return (System.nanoTime()-start)/1_000_000; }
    private static Result failure(String outcome,String message,String reason,Throwable cause) {
        return new Result("【本次选路失败】\n"+message+"\n未生成有效去向，未自动重试或执行业务操作。",outcome,reason,type(cause));
    }
    private static String stageMessage(Stage stage) {
        return switch(stage) {
            case POLICY_INIT->"选路规则初始化失败，请检查规则配置。";
            case MODEL_ISOLATION->"选路模型隔离或客户端初始化失败，请检查模型适配配置。";
            case PRECALCULATION->"服务端条件预计算失败，请检查数据与计算逻辑。";
            case MODEL_CALL->"模型调用失败，请检查模型服务。";
            case METADATA->"模型响应统计读取失败，请检查响应适配。";
            case BODY->"模型响应正文读取失败。";
            case VALIDATION,REPORT->"服务端选路核验或报告生成失败。";
            default->"选路服务内部处理失败。";
        };
    }
    private Result run(String input,Invocation call) {
        Result outcome=null;
        try {
            RoutingInput snapshot=RoutingInput.parse(input);
            call.stage=Stage.POLICY_INIT;RoutingPolicy activePolicy=policy();call.promptHash=activePolicy.fingerprint();
            call.stage=Stage.PRECALCULATION;var facts=precalculator.apply(snapshot);
            call.stage=Stage.MODEL_ISOLATION;ChatClient activeClient=client();
            call.stage=Stage.PROMPT;
            var prompt=new Prompt(List.of(new SystemMessage(activePolicy.prompt()),new UserMessage(RoutingInput.writeJson(activePolicy.modelInput(facts)))));
            // A timeout during queueing/initialization must not start a new provider request.
            if(call.delivery.get()!=Delivery.WAITING)return outcome=failure("ABANDONED","页面已停止等待。","BEFORE_MODEL_CALL",null);
            call.stage=Stage.MODEL_CALL;long start=System.nanoTime();
            org.springframework.ai.chat.model.ChatResponse response;
            try { response=activeClient.prompt(prompt).call().chatResponse(); }
            finally { call.callMs=elapsed(start); }
            call.stage=Stage.METADATA;
            if(response!=null) {
                var metadata=response.getMetadata();var usage=metadata==null?null:metadata.getUsage();
                call.tokens=usage==null?null:usage.getTotalTokens(); // Preserve a reported zero.
                call.modelId=metadata==null?null:metadata.getModel();
            }
            // Always audit returned usage, including returns after timeout/cancellation. No body is logged.
            LOG.info("选路模型返回 requestId={} delivery={} model={} callMs={} totalTokens={} usageState={}",
                call.id,call.delivery.get(),call.modelId,call.callMs,call.tokens,call.tokens==null?"UNAVAILABLE":"REPORTED");
            if(call.delivery.get()!=Delivery.WAITING)return outcome=failure("LATE_RESPONSE_IGNORED","页面已停止等待。","AFTER_MODEL_CALL",null);
            call.stage=Stage.BODY;
            var generation=response==null?null:response.getResult();
            var output=generation==null?null:generation.getOutput();String text=output==null?null:output.getText();
            if(text==null||text.isBlank())return outcome=failure("EMPTY_MODEL_OUTPUT","模型未返回可用文本。","EMPTY_OUTPUT",null);
            call.stage=Stage.VALIDATION;var decision=activePolicy.validate(text,facts);
            call.stage=Stage.REPORT;
            String report=activePolicy.display(decision,facts)+"\n模型调用耗时："+call.callMs+"ms；Token："+(call.tokens==null?"服务未提供":call.tokens)+"；"+COST_NOTICE;
            return outcome=new Result(report,decision.status().name(),"VALIDATED_REPORT","none");
        } catch(RoutingModelFactory.IsolationFailure e) {
            return outcome=failure("MODEL_ISOLATION_FAILED",stageMessage(Stage.MODEL_ISOLATION),e.reason(),e.getCause()==null?e:e.getCause());
        } catch(RoutingPolicy.Rejected e) {
            return outcome=failure("MODEL_OUTPUT_REJECTED",e.getMessage(),e.audit().toString()+" parseDiagnostic="+e.parseDiagnostic(),e);
        } catch(RoutingPolicy.InternalFailure e) {
            return outcome=failure("SERVER_ERROR",stageMessage(Stage.REPORT),e.phase().name(),e.getCause());
        } catch(Exception e) {
            if(call.stage==Stage.INPUT && e instanceof IllegalArgumentException)
                return outcome=new Result("【输入不合要求，未执行选路】\n"+e.getMessage(),"INPUT_REJECTED","INPUT_CONTRACT",type(e));
            return outcome=failure(call.stage+"_FAILED",stageMessage(call.stage),call.stage.name(),e);
        } finally {
            Delivery delivery=call.delivery.get();
            if(delivery==Delivery.TIMEOUT||delivery==Delivery.CANCELLED)
                LOG.info("选路后台结束 requestId={} delivery={} stage={} workerOutcome={} causeType={} elapsedMs={} callMs={} totalTokens={}",
                    call.id,delivery,call.stage,outcome==null?"UNEXPECTED_TERMINATION":outcome.outcome(),outcome==null?"unknown":outcome.causeType(),elapsed(call.start),call.callMs,call.tokens);
        }
    }
    private void audit(Invocation call,Result result) {
        LOG.info("选路实验 requestId={} version={} promptHash={} model={} outcome={} stage={} reason={} causeType={} elapsedMs={} callMs={} totalTokens={}",
            call.id,RoutingInput.VERSION,call.promptHash,call.modelId,result.outcome(),call.stage,result.reason(),result.causeType(),elapsed(call.start),call.callMs,call.tokens);
    }
    public Mono<String> answer(String input) {
        return Mono.defer(()->{
            Invocation call=new Invocation();
            // Reactor cancels the subscription on timeout. It may interrupt the worker, but the
            // provider/HTTP client may ignore interruption and still consume tokens. Their own
            // connection/read timeout settings remain authoritative for transport termination.
            return Mono.fromCallable(()->run(input,call)).subscribeOn(Schedulers.boundedElastic())
                .timeout(Mono.delay(responseTimeout).doOnNext(ignored->call.delivery.compareAndSet(Delivery.WAITING,Delivery.TIMEOUT)),Mono.defer(()->{
                    return Mono.just(failure("RESPONSE_TIMEOUT","页面等待已超时；底层模型请求可能仍在运行，请勿认为调用已终止。","RESPONSE_BUDGET",null));
                }))
                .onErrorResume(e->Mono.just(failure("ASYNC_FAILURE","异步选路任务异常。",call.stage.name(),e)))
                .doOnNext(result->{call.delivery.compareAndSet(Delivery.WAITING,Delivery.DELIVERED);audit(call,result);})
                .map(Result::text)
                .doOnCancel(()->{
                    if(call.delivery.compareAndSet(Delivery.WAITING,Delivery.CANCELLED))
                        LOG.info("选路实验 requestId={} outcome=CLIENT_CANCELLED stage={} elapsedMs={}",call.id,call.stage,elapsed(call.start));
                });
        });
    }
}
