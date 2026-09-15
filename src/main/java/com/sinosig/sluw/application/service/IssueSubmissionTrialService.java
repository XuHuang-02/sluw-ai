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

/** Reuses the existing trial entry: isolated current snapshot, no advisors, RAG or business tools. */
@Service
public class IssueSubmissionTrialService {
    private static final Logger LOG=LoggerFactory.getLogger(IssueSubmissionTrialService.class);
    private volatile ChatClient client;
    private final java.util.function.Supplier<ChatModel> modelSupplier;
    private final RoutingPolicy policy;
    @org.springframework.beans.factory.annotation.Autowired
    public IssueSubmissionTrialService(ChatModel model, RoutingModelFactory factory) throws java.io.IOException {
        this(() -> factory.isolate(model));
    }
    IssueSubmissionTrialService(ChatModel model) throws java.io.IOException { this(() -> model); }
    private IssueSubmissionTrialService(java.util.function.Supplier<ChatModel> supplier) throws java.io.IOException {
        modelSupplier=supplier;policy=new RoutingPolicy();
    }
    private synchronized ChatClient client() {
        if(client==null)client=ChatClient.builder(modelSupplier.get()).build();
        return client;
    }
    public Mono<String> answer(String input) {
        return Mono.fromCallable(()->{
            RoutingInput snapshot;
            try { snapshot=RoutingInput.parse(input); }
            catch(IllegalArgumentException e){return "【输入不合要求，未执行选路】\n"+e.getMessage();}
            var facts=new RoutingPrecalculator(snapshot).calculate();
            long start=System.nanoTime();
            var response=client().prompt(new Prompt(List.of(new SystemMessage(policy.prompt()),
                new UserMessage(RoutingInput.writeJson(policy.modelInput(facts)))))).call().chatResponse();
            if(response==null||response.getResult()==null)throw new IllegalStateException("empty response");
            long elapsed=(System.nanoTime()-start)/1_000_000;
            Integer reported=response.getMetadata().getUsage().getTotalTokens();
            Integer tokens=reported!=null&&reported>0?reported:null;
            String modelId=response.getMetadata().getModel();
            try {
                var result=policy.validate(response.getResult().getOutput().getText(),facts);
                LOG.info("选路实验 version={} promptHash={} model={} outcome={} modelFormatCorrect=true modelBranchCorrect=true serverReportCorrect=true elapsedMs={} totalTokens={}",RoutingInput.VERSION,policy.fingerprint(),modelId,result.status(),elapsed,tokens);
                return policy.display(result,facts)+"\n模型调用耗时："+elapsed+"ms；Token："+(tokens==null?"服务未提供":tokens)+"；费用：未配置价格，不估算。";
            }catch(RoutingPolicy.Rejected e){
                LOG.info("选路实验 version={} promptHash={} model={} outcome=REJECTED audit={} elapsedMs={} totalTokens={}",RoutingInput.VERSION,policy.fingerprint(),modelId,e.audit(),elapsed,tokens);
                return "【本次选路失败】\n"+e.getMessage()+"\n未生成有效去向，未自动修改业务内容或重试，未执行业务操作。";
            }catch(RoutingPolicy.InternalFailure e){
                LOG.warn("选路实验 version={} promptHash={} outcome=SERVER_ERROR phase={} causeType={} elapsedMs={} totalTokens={}",
                    RoutingInput.VERSION,policy.fingerprint(),e.phase(),e.getCause().getClass().getSimpleName(),elapsed,tokens);
                return "【本次选路失败】\n服务端规则核验或报告组装失败，未生成有效去向，未重试或执行业务操作。";
            }catch(IllegalStateException e){
                LOG.warn("选路实验 version={} outcome=SERVER_REPORT_FAILED elapsedMs={} totalTokens={}",RoutingInput.VERSION,elapsed,tokens);
                return "【本次选路失败】\n服务端规则核验或报告组装失败，未生成有效去向，未重试或执行业务操作。";
            }
        }).subscribeOn(Schedulers.boundedElastic()).timeout(Duration.ofSeconds(50))
          .onErrorResume(e->{LOG.warn("选路实验 version={} outcome=FAILED",RoutingInput.VERSION);
              return Mono.just("【本次选路失败】\n模型调用或条件计算未完成，未生成有效去向。未执行业务操作，请核查服务后重新提交完整JSON。");});
    }
}
