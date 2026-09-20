package com.sinosig.sluw.application.edd.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import static org.junit.jupiter.api.Assertions.*;

class EddDraftLiveConfigurationTest {
    @org.springframework.context.annotation.Configuration(proxyBeanMethods=false)
    static class OfflineTransport {
        @org.springframework.context.annotation.Bean
        org.springframework.web.client.RestClient.Builder restClientBuilder() {
            return org.springframework.web.client.RestClient.builder().requestFactory(new org.springframework.http.client.SimpleClientHttpRequestFactory());
        }
        @org.springframework.context.annotation.Bean
        org.springframework.web.reactive.function.client.WebClient.Builder webClientBuilder() {
            return org.springframework.web.reactive.function.client.WebClient.builder().clientConnector(
                (method,uri,callback)->reactor.core.publisher.Mono.error(new IllegalStateException("No network in configuration test")));
        }
    }
    @Test void existingModelAutoconfigurationStartsWithoutBusinessInfrastructure() {
        for(String provider:new String[]{"deepseek","dashscope"}) {
            try(var context=new org.springframework.boot.builder.SpringApplicationBuilder(EddDraftLiveTest.ModelConfiguration.class,OfflineTransport.class)
                    .web(org.springframework.boot.WebApplicationType.NONE).bannerMode(org.springframework.boot.Banner.Mode.OFF).logStartupInfo(false).run(
                    "--spring.config.location=optional:classpath:/edd-isolated-test.properties",
                    "--spring.ai.model.chat="+provider,
                    "--spring.ai.deepseek.api-key=synthetic-never-sent",
                    "--spring.ai.dashscope.api-key=synthetic-never-sent")) {
                assertEquals(1,context.getBeansOfType(ChatModel.class).size());
                assertTrue(context.getBean(ChatModel.class).getClass().getSimpleName().toLowerCase().contains(provider));
                assertFalse(context.containsBean("dataSource"));assertFalse(context.containsBean("redisTemplate"));
            }
        }
    }
    @Test void longLiveFixtureUsesAllHistoryAndTailCash() throws Exception {
        var input=EddDraftLiveTest.fixture("SYN-V1-018");
        assertTrue(input.path("synthetic").asBoolean());assertEquals(10000,input.path("events").size());
        assertEquals("cash",input.path("events").get(9999).path("payment_method").asText());
    }
}
