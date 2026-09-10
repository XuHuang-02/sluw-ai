package com.sinosig.sluw.application.authority;

import com.sinosig.sluw.application.commons.web.ConfigReader;
import com.sinosig.sluw.application.commons.web.SpringCtxUtil;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SecurityConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        ConfigReader configReader = SpringCtxUtil.getSpringContext().getBean(ConfigReader.class);
        String openFlag = configReader.getProperty("qnb", "openFlag");
        if ("Y".equals(openFlag)){
            // 拦截所有访问静态页面的请求，进行鉴权
            registry.addInterceptor(new TokenAuthInterceptor())
                    .addPathPatterns("/","/static/**") // 保护根路径和静态资源路径
            //.excludePathPatterns("/static/public/**")
            ; // 可配置公开路径
        }

    }
}
