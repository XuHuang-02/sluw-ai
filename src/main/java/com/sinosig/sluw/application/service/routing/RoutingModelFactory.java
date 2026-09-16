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
    private static final org.springframework.ai.model.tool.ToolExecutionEligibilityPredicate NEVER_EXECUTE = (options,response)->false;
    public static final class IsolationFailure extends IllegalStateException {
        private final String reason;
        IsolationFailure(String reason,Throwable cause) { super("选路模型适配失败："+reason,cause);this.reason=reason; }
        public String reason() { return reason; }
    }
    private static final RetryTemplate ONCE = RetryTemplate.builder().maxAttempts(1).build();

    public ChatModel isolate(ChatModel original) {
        if(org.springframework.aop.support.AopUtils.isAopProxy(original)) {
            if(!(original instanceof org.springframework.aop.framework.Advised advised))
                throw new IsolationFailure("OPAQUE_PROXY",null);
            return isolateProxy(original,advised);
        }
        if (original instanceof DashScopeChatModel dash) {
            requireStandardModel(original, DashScopeChatModel.class);
            var configured = dash.getDashScopeChatOptions();
            DashScopeChatOptions options = configured == null ? DashScopeChatOptions.builder().build()
                : (DashScopeChatOptions) configured.copy();
            options.setTemperature(0.0);
            disableTools(options);
            return dash.mutate().defaultOptions(options).retryTemplate(ONCE)
                .toolExecutionEligibilityPredicate(NEVER_EXECUTE)
                .observationRegistry(ObservationRegistry.NOOP).build();
        }
        if (original instanceof DeepSeekChatModel deepSeek) {
            requireStandardModel(original, DeepSeekChatModel.class);
            var configured = deepSeek.getDefaultOptions();
            if (configured != null && !(configured instanceof DeepSeekChatOptions))
                throw new IsolationFailure("OPTIONS_TYPE",null);
            var options = configured == null ? DeepSeekChatOptions.builder().build()
                : ((DeepSeekChatOptions) configured).copy();
            options.setTemperature(0.0);
            disableTools(options);
            return DeepSeekChatModel.builder().deepSeekApi(configuredApi(deepSeek))
                .defaultOptions(options).retryTemplate(ONCE)
                .toolExecutionEligibilityPredicate(NEVER_EXECUTE)
                .observationRegistry(ObservationRegistry.NOOP).build();
        }
        throw new IsolationFailure("UNSUPPORTED_MODEL",null);
    }

    private static void disableTools(org.springframework.ai.model.tool.ToolCallingChatOptions options) {
        // Clearing local callbacks/names removes tool definitions. The switch prevents internal
        // execution; NEVER_EXECUTE separately blocks execution eligibility for returned calls.
        options.setToolCallbacks(java.util.List.of());options.setToolNames(java.util.Set.of());
        options.setInternalToolExecutionEnabled(false);
    }
    private ChatModel isolateProxy(ChatModel original,org.springframework.aop.framework.Advised advised) {
        Class<?> targetClass=org.springframework.aop.support.AopUtils.getTargetClass(original);
        var source=advised.getTargetSource();
        var proxy=new org.springframework.aop.framework.ProxyFactory();
        proxy.setInterfaces(advised.getProxiedInterfaces());proxy.setProxyTargetClass(advised.isProxyTargetClass());
        proxy.setExposeProxy(advised.isExposeProxy());proxy.setPreFiltered(advised.isPreFiltered());
        // Keep advisors and the target-source lifecycle. Do not unwrap lazy/refresh/pooled
        // targets once and retain a stale target, or silently discard AOP behavior.
        proxy.setTargetSource(new org.springframework.aop.TargetSource() {
            private final java.util.Map<Object,Object> leases=java.util.Collections.synchronizedMap(new java.util.IdentityHashMap<>());
            public Class<?> getTargetClass() { return targetClass; }
            public boolean isStatic() { return false; }
            public Object getTarget() throws Exception {
                Object target;
                try { target=source.getTarget(); }
                catch(Exception e) { throw new IsolationFailure("PROXY_TARGET_RESOLUTION",e); }
                try {
                    if(!(target instanceof ChatModel model))throw new IsolationFailure("PROXY_TARGET_TYPE",null);
                    ChatModel isolated=isolate(model);leases.put(isolated,target);return isolated;
                } catch(RuntimeException e) {
                    if(target!=null)try { source.releaseTarget(target); }catch(Exception release) { e.addSuppressed(release); }
                    throw e;
                }
            }
            public void releaseTarget(Object target) throws Exception {
                Object originalTarget=leases.remove(target);
                if(originalTarget!=null)source.releaseTarget(originalTarget);
            }
        });
        for(var advisor:advised.getAdvisors())proxy.addAdvisor(advisor);
        proxy.setFrozen(advised.isFrozen());
        return (ChatModel)proxy.getProxy(original.getClass().getClassLoader());
    }

    private static void requireStandardModel(ChatModel model, Class<?> supported) {
        if (model.getClass() != supported)
            throw new IsolationFailure("CUSTOM_MODEL_SUBCLASS",null);
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
            throw new IllegalStateException("configured API is missing");
        } catch (RuntimeException cause) {
            // Preserve the cause for diagnosis, but callers log only its type, never its message.
            throw new IsolationFailure("DEEPSEEK_API_ACCESS",cause);
        }
    }
}
