package com.sinosig.sluw.application.service.routing;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.ai.deepseek.*;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.model.deepseek.autoconfigure.*;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.web.client.*;
import org.springframework.web.reactive.function.client.WebClient;
import com.alibaba.cloud.ai.dashscope.chat.*;
import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import java.util.concurrent.atomic.AtomicInteger;

class RoutingModelFactoryTest {
    private RestClient.Builder rest() {return RestClient.builder().requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory());}
    private WebClient.Builder web() {return WebClient.builder().clientConnector((method,uri,callback)->reactor.core.publisher.Mono.error(new IllegalStateException("No network in unit tests")));}
    private RoutingModelFactory factory() {
        var beans=new DefaultListableBeanFactory();
        var connection=new DeepSeekConnectionProperties();connection.setApiKey("synthetic-test-key");
        beans.registerSingleton("rest",rest());beans.registerSingleton("web",web());
        beans.registerSingleton("connection",connection);beans.registerSingleton("chat",new DeepSeekChatProperties());
        return new RoutingModelFactory(beans.getBeanProvider(DeepSeekChatProperties.class),beans.getBeanProvider(DeepSeekConnectionProperties.class),
            beans.getBeanProvider(RestClient.Builder.class),beans.getBeanProvider(WebClient.Builder.class),beans.getBeanProvider(ResponseErrorHandler.class));
    }
    private int attempts(RetryTemplate retry) {
        var count=new AtomicInteger();
        assertThrows(IllegalStateException.class,()->retry.execute(context->{count.incrementAndGet();throw new IllegalStateException("synthetic");}));
        return count.get();
    }
    @Test void deepseekKeepsOrdinaryRetryAndOptionsUnchanged() {
        var ordinary=DeepSeekChatModel.builder().deepSeekApi(DeepSeekApi.builder().apiKey("synthetic").restClientBuilder(rest()).webClientBuilder(web()).build())
            .defaultOptions(DeepSeekChatOptions.builder().model("deepseek-chat").temperature(0.7).build())
            .retryTemplate(RetryTemplate.builder().maxAttempts(3).noBackoff().build()).build();
        var trial=(DeepSeekChatModel)factory().isolate(ordinary);
        assertNotSame(ordinary,trial);assertEquals(1,attempts(trial.retryTemplate));assertEquals(3,attempts(ordinary.retryTemplate));
        assertEquals(0.7,ordinary.getDefaultOptions().getTemperature());assertEquals(0.0,trial.getDefaultOptions().getTemperature());
        var options=(DeepSeekChatOptions)trial.getDefaultOptions();assertFalse(options.getInternalToolExecutionEnabled());assertTrue(options.getToolCallbacks().isEmpty());
    }
    @Test void dashscopeKeepsOrdinaryRetryAndOptionsUnchanged() {
        var ordinary=DashScopeChatModel.builder().dashScopeApi(DashScopeApi.builder().apiKey("synthetic").restClientBuilder(rest()).webClientBuilder(web()).build())
            .defaultOptions(DashScopeChatOptions.builder().withModel("qwen-plus").withTemperature(0.7).build())
            .retryTemplate(RetryTemplate.builder().maxAttempts(3).noBackoff().build()).build();
        var trial=(DashScopeChatModel)factory().isolate(ordinary);
        assertNotSame(ordinary,trial);assertEquals(1,attempts(trial.retryTemplate));assertEquals(3,attempts(ordinary.retryTemplate));
        assertEquals(0.7,ordinary.getDefaultOptions().getTemperature());assertEquals(0.0,trial.getDefaultOptions().getTemperature());
        assertFalse(trial.getDashScopeChatOptions().getInternalToolExecutionEnabled());assertTrue(trial.getDashScopeChatOptions().getToolCallbacks().isEmpty());
    }
}
