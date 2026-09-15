package com.sinosig.sluw.application.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sinosig.sluw.application.commons.utils.AesUtil;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 聊天信息加密过滤器
 * 在请求进入DispatcherServlet之前，对聊天信息进行加密并重新包装请求。
 */
@Component
public class RequestDecryptionFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(RequestDecryptionFilter.class);

    private static final String TARGET_PATH = "/api/agent/chat/stream";
    private static final String TCHAT_PATH = "/ai/api/chat";
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Bean
    public FilterRegistrationBean<RequestDecryptionFilter> requestDecryptionFilterRegistration(RequestDecryptionFilter filter) {
        FilterRegistrationBean<RequestDecryptionFilter> registrationBean = new FilterRegistrationBean<>();
        // 设置要注册的过滤器实例，参数filter由Spring自动注入
        registrationBean.setFilter(filter);
        // 设置要拦截的URL模式
        registrationBean.addUrlPatterns(TARGET_PATH,TCHAT_PATH);
        // 设置执行顺序
        registrationBean.setOrder(1);
        // 设置过滤器名称，可选
        registrationBean.setName("requestDecryptionFilter");
        return registrationBean;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String requestURI = httpRequest.getRequestURI();

        //  仅对目标路径的POST请求进行解密处理
        if (TARGET_PATH.equals(requestURI) && "POST".equalsIgnoreCase(httpRequest.getMethod())) {

            //  读取并解密请求体
            String encryptedRequestBody = StreamUtils.copyToString(request.getInputStream(), StandardCharsets.UTF_8);
            String decryptedRequestBody = null;

            try {
                // 解析JSON，获取加密的问题字段并解密
                JsonNode rootNode = objectMapper.readTree(encryptedRequestBody);
                String encryptedQuestion = rootNode.get("question").asText();
                String decryptedQuestion = AesUtil.decrypt(encryptedQuestion);
                logger.debug("聊天请求解密完成，字符数={}", decryptedQuestion.length());

                //  用解密后的问题替换原JSON中的字段
                ((com.fasterxml.jackson.databind.node.ObjectNode) rootNode).put("question", decryptedQuestion);
                decryptedRequestBody = objectMapper.writeValueAsString(rootNode);

            } catch (Exception e) {
                // 解密失败
                logger.error("问题内容解密失败", e);
                throw new ServletException("问题内容解密失败", e);
            }

            //  将解密后的请求体封装到新的HttpServletRequestWrapper中，继续传递
            final String finalDecryptedBody = decryptedRequestBody;
            HttpServletRequestWrapper wrappedRequest = new HttpServletRequestWrapper(httpRequest) {
                @Override
                public ServletInputStream getInputStream() throws IOException {
                    ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(finalDecryptedBody.getBytes(StandardCharsets.UTF_8));
                    return new ServletInputStream() {
                        @Override
                        public int read() throws IOException {
                            return byteArrayInputStream.read();
                        }
                        @Override
                        public boolean isFinished() {
                            return byteArrayInputStream.available() == 0;
                        }
                        @Override
                        public boolean isReady() {
                            return true;
                        }
                        @Override
                        public void setReadListener(ReadListener readListener) {
                            // 无需实现
                        }
                    };
                }
            };
            chain.doFilter(wrappedRequest, response);
        } else {
            // 非目标路径，直接放行
            chain.doFilter(request, response);
        }
    }

}