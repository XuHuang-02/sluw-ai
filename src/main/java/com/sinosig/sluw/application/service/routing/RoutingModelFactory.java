package com.sinosig.sluw.application.service.routing;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.ai.model.deepseek.autoconfigure.DeepSeekChatProperties;
import org.springframework.ai.model.deepseek.autoconfigure.DeepSeekConnectionProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.reactive.function.client.WebClient;

/** Reuses configured provider credentials/transports without mutating the ordinary chat model. */
@Component
public final class RoutingModelFactory {
    private final ObjectProvider<DeepSeekChatProperties> chatProperties;
    private final ObjectProvider<DeepSeekConnectionProperties> connectionProperties;
    private final ObjectProvider<RestClient.Builder> rest;
    private final ObjectProvider<WebClient.Builder> web;
    private final ObjectProvider<ResponseErrorHandler> errors;
    public RoutingModelFactory(ObjectProvider<DeepSeekChatProperties> chatProperties,
        ObjectProvider<DeepSeekConnectionProperties> connectionProperties, ObjectProvider<RestClient.Builder> rest,
        ObjectProvider<WebClient.Builder> web,ObjectProvider<ResponseErrorHandler> errors) {
        this.chatProperties=chatProperties;this.connectionProperties=connectionProperties;this.rest=rest;this.web=web;this.errors=errors;
    }
    public ChatModel isolate(ChatModel original) {
        var once=RetryTemplate.builder().maxAttempts(1).build();
        if(original instanceof DashScopeChatModel dash) {
            com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions options=(com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions)dash.getDashScopeChatOptions().copy();
            options.setTemperature(0.0);options.setToolCallbacks(java.util.List.of());options.setToolNames(java.util.Set.of());options.setInternalToolExecutionEnabled(false);
            return dash.mutate().defaultOptions(options).retryTemplate(once)
                .toolExecutionEligibilityPredicate((optionsIgnored,response)->false)
                .observationRegistry(io.micrometer.observation.ObservationRegistry.NOOP).build();
        }
        if(original instanceof DeepSeekChatModel) {
            var chat=chatProperties.getObject();var connection=connectionProperties.getObject();
            var api=DeepSeekApi.builder()
                .baseUrl(StringUtils.hasText(chat.getBaseUrl())?chat.getBaseUrl():connection.getBaseUrl())
                .apiKey(StringUtils.hasText(chat.getApiKey())?chat.getApiKey():connection.getApiKey())
                .completionsPath(chat.getCompletionsPath()).betaPrefixPath(chat.getBetaPrefixPath())
                .restClientBuilder(rest.getIfAvailable(RestClient::builder).clone())
                .webClientBuilder(web.getIfAvailable(WebClient::builder).clone());
            var handler=errors.getIfAvailable();if(handler!=null)api.responseErrorHandler(handler);
            var options=((DeepSeekChatOptions)original.getDefaultOptions()).copy();
            options.setTemperature(0.0);options.setToolCallbacks(java.util.List.of());options.setToolNames(java.util.Set.of());options.setInternalToolExecutionEnabled(false);
            return DeepSeekChatModel.builder().deepSeekApi(api.build()).defaultOptions(options).retryTemplate(once)
                .toolExecutionEligibilityPredicate((optionsIgnored,response)->false).build();
        }
        throw new IllegalStateException("当前模型尚未配置独立的单次选路调用适配。");
    }
}
