package com.sinosig.sluw.application.filter;


import com.sinosig.sluw.application.commons.utils.AesUtil;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

@Aspect
@Component
@Order(1)
public class ResponseSSEEncryptionAspect {
    private static final Logger logger = LoggerFactory.getLogger(ResponseSSEEncryptionAspect.class);

    @Around("execution(* com.sinosig.sluw.application.controller.AgentController.chatStream(..))")
    public Object encryptSSEResponse(ProceedingJoinPoint joinPoint) throws Throwable {
        // 执行原始方法
        Object result = joinPoint.proceed();
        if (result instanceof Flux) {
            @SuppressWarnings("unchecked")
            Flux<ServerSentEvent<String>> originalFlux = (Flux<ServerSentEvent<String>>) result;
            // 转换 Flux，加密其中的数据
            return originalFlux.map(sse -> {
                if (sse.data() != null) {
                    String data = sse.data();
                    if (!"[DONE]".equals(data) && !data.startsWith("[ERROR]")) {
                        try {
                            String encryptedData = AesUtil.encrypt(data);
                            return ServerSentEvent.builder(encryptedData)
                                    .id(sse.id())
                                    .event(sse.event())
                                    .retry(sse.retry())
                                    .comment(sse.comment())
                                    .build();
                        } catch (Exception e) {
                            return ServerSentEvent.builder("[ERROR] 加密失败: " + e.getMessage())
                                    .build();
                        }
                    }
                }
                return sse;
            });
        }

        return result;
    }
}