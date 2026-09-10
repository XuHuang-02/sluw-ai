package com.sinosig.sluw.application.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sinosig.sluw.application.commons.entity.AssistantTrackEntity;
import com.sinosig.sluw.application.commons.service.AssistantTrackService;
import com.sinosig.sluw.application.commons.utils.AesUtil;
import com.sinosig.sluw.application.commons.utils.DateUtil;
import com.sinosig.sluw.application.commons.web.SpringCtxUtil;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 聊天响应加密过滤器
 * 在响应返回给客户端之前，对聊天回答进行加密。
 */
@Component
public class ResponseEncryptionFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(ResponseEncryptionFilter.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private static final String TCHAT_PATH = "/ai/api/chat";

    @Bean
    public FilterRegistrationBean<ResponseEncryptionFilter> responseDecryptionFilterRegistration(ResponseEncryptionFilter filter) {
        FilterRegistrationBean<ResponseEncryptionFilter> registrationBean = new FilterRegistrationBean<>();
        // 设置要注册的过滤器实例，参数filter由Spring自动注入
        registrationBean.setFilter(filter);
        // 设置要拦截的URL模式
        registrationBean.addUrlPatterns(TCHAT_PATH);
        // 设置执行顺序
        registrationBean.setOrder(2);
        // 设置过滤器名称，可选
        registrationBean.setName("responseDecryptionFilter");
        return registrationBean;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        //  只对特定路径的响应进行处理
        if (TCHAT_PATH.equals(request.getRequestURI())) {
            //  使用 ContentCachingResponseWrapper 包装原始响应，以便后续读取响应体
            ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

            try {
                //  继续执行过滤链（包括控制器方法）
                filterChain.doFilter(request, responseWrapper);

                //  链执行完毕后，获取缓存的响应内容
                byte[] responseBody = responseWrapper.getContentAsByteArray();
                if (responseBody.length > 0) {
                    String responseBodyStr = new String(responseBody, StandardCharsets.UTF_8);
                    logger.info("原始响应体: {}", responseBodyStr);

                    //  解析并加密 ChatResponse
                    try {
                        Map<String, Object> responseEntityMap = objectMapper.readValue(responseBodyStr, Map.class);
                        if (responseEntityMap.containsKey("answer") && responseEntityMap.get("answer") instanceof String) {
                            String answer = (String) responseEntityMap.get("answer");
                            String encryptedAnswer = AesUtil.encrypt(answer);
                            responseEntityMap.put("answer", encryptedAnswer);
                            String encryptedBodyStr = objectMapper.writeValueAsString(responseEntityMap);
                            byte[] encryptedBody = encryptedBodyStr.getBytes(StandardCharsets.UTF_8);
                            logger.info("加密后响应体: {}", encryptedBodyStr);

                            //  数据入库
                            saveTrack(request,answer);

                            //  重置原始响应的Content-Length，并写入加密后的数据
                            response.setContentLength(encryptedBody.length);
                            response.getOutputStream().write(encryptedBody);
                        }else {
                            // 如果answer为null，直接拷贝原始响应
                            copyBodyToResponse(responseBody, response);
                        }

                    } catch (Exception e) {
                        // 如果响应体不是ChatResponse格式（例如错误信息），则原样返回
                        logger.error("响应体非ChatResponse格式，跳过加密: {}", e.getMessage());
                        copyBodyToResponse(responseBody, response);
                    }
                }

            } finally {
                // 确保包装器的内容被消费，避免内存泄漏
                responseWrapper.copyBodyToResponse();
            }
        } else {
            // 非目标路径，直接放行
            filterChain.doFilter(request, response);
        }
    }

    /**
     * 辅助方法：将字节数组内容写入原始响应
     */
    private void copyBodyToResponse(byte[] body, HttpServletResponse response) throws IOException {
        response.setContentLength(body.length);
        response.getOutputStream().write(body);
    }

    public void saveTrack(HttpServletRequest request, String answer){
        try {
            AssistantTrackEntity assistantTrack = new AssistantTrackEntity();
            byte[] originalBody = StreamUtils.copyToByteArray(request.getInputStream());
            String originalBodyStr = new String(originalBody, StandardCharsets.UTF_8);
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, String> jsonMap = objectMapper.readValue(originalBodyStr, Map.class);
            String question = jsonMap.get("question"); //问题
            String conversationId = jsonMap.get("conversationId"); //会话ID
            assistantTrack.setQuestion(question);
            assistantTrack.setAnswer(answer);
            assistantTrack.setConversationId(conversationId);

            Object userInfo = request.getSession().getAttribute("currentUserInfo");
            if (userInfo != null){
                Map<String, String> userInfoMap= (Map<String, String>) userInfo;
                String userCode = userInfoMap.get("userCode"); //工号
                String userType = userInfoMap.get("userType"); //用户类型
                String manageCom = userInfoMap.get("manageCom"); //机构代码
                assistantTrack.setUserCode(userCode);
                assistantTrack.setUserType(userType);
                assistantTrack.setManageCom(manageCom);
            }
            assistantTrack.setMakeDate(DateUtil.date());
            assistantTrack.setMakeTime(DateUtil.ftime());
            assistantTrack.setModifyDate(DateUtil.date());
            assistantTrack.setModifyTime(DateUtil.ftime());

            AssistantTrackService assistantTrackService = SpringCtxUtil.getSpringContext().getBean(AssistantTrackService.class);
            assistantTrackService.save(assistantTrack);
        }catch (Exception e){
            logger.error("数据保存失败！");
            logger.error(e.getMessage(),e);
        }
    }
}
