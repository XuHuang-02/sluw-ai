package com.sinosig.sluw.application.commons.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/ai/**") // 匹配所有以/ai开头的路径
                .allowedOrigins("*") // 允许所有来源
                .allowedMethods("GET", "POST") // 允许的HTTP方法
                .allowedHeaders("*"); // 允许所有请求头
    }
}