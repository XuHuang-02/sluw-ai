package com.sinosig.sluw.application.service.routing;

import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

/** Copies model options while sharing the configured API/transport; never changes ordinary chat. */
@Component
public final class RoutingModelFactory {
    private static final RetryTemplate ONCE = RetryTemplate.builder().maxAttempts(1).build();

    public ChatModel isolate(ChatModel original) {
        if (original instanceof DashScopeChatModel dash) {
            requireStandardModel(original, DashScopeChatModel.class);
            var configured = dash.getDashScopeChatOptions();
            DashScopeChatOptions options = configured == null ? DashScopeChatOptions.builder().build()
                : (DashScopeChatOptions) configured.copy();
            options.setTemperature(0.0);
            options.setToolCallbacks(java.util.List.of());
            options.setToolNames(java.util.Set.of());
            options.setInternalToolExecutionEnabled(false);
            return dash.mutate().defaultOptions(options).retryTemplate(ONCE)
                .toolExecutionEligibilityPredicate((ignored, response) -> false)
                .observationRegistry(ObservationRegistry.NOOP).build();
        }
        if (original instanceof DeepSeekChatModel deepSeek) {
            requireStandardModel(original, DeepSeekChatModel.class);
            var configured = deepSeek.getDefaultOptions();
            if (configured != null && !(configured instanceof DeepSeekChatOptions))
                throw new IllegalStateException("DeepSeek默认选项类型不兼容，未创建选路模型。");
            var options = configured == null ? DeepSeekChatOptions.builder().build()
                : ((DeepSeekChatOptions) configured).copy();
            options.setTemperature(0.0);
            options.setToolCallbacks(java.util.List.of());
            options.setToolNames(java.util.Set.of());
            options.setInternalToolExecutionEnabled(false);
            return DeepSeekChatModel.builder().deepSeekApi(configuredApi(deepSeek))
                .defaultOptions(options).retryTemplate(ONCE)
                .toolExecutionEligibilityPredicate((ignored, response) -> false)
                .observationRegistry(ObservationRegistry.NOOP).build();
        }
        throw new IllegalStateException("当前模型尚未配置独立的单次选路调用适配。");
    }

    private static void requireStandardModel(ChatModel model, Class<?> supported) {
        if (model.getClass() != supported)
            throw new IllegalStateException("自定义模型子类需要显式的选路适配，不能通过重建丢弃其定制行为。");
    }

    /**
     * Spring AI 1.1.2 has no DeepSeek mutate() or public API accessor, and auto-configuration
     * does not expose its API as a bean. Keep this read-only compatibility seam in one place.
     * Reuse the exact API (including headers, transports and API customizations); never rebuild
     * it from properties. Fail closed if the dependency changes or reflective access is denied.
     * Replace with the public copy API when the project's dependency provides one.
     */
    private static DeepSeekApi configuredApi(DeepSeekChatModel model) {
        try {
            var field = ReflectionUtils.findField(DeepSeekChatModel.class, "deepSeekApi", DeepSeekApi.class);
            if (field == null) throw new IllegalStateException("missing API field");
            ReflectionUtils.makeAccessible(field);
            Object api = ReflectionUtils.getField(field, model);
            if (api instanceof DeepSeekApi configured) return configured;
        } catch (RuntimeException ignored) {
            // Do not log provider internals or fall back to a potentially different connection.
        }
        throw new IllegalStateException("当前DeepSeek依赖不支持复用已配置API，未创建选路模型；请检查版本适配。");
    }
}
