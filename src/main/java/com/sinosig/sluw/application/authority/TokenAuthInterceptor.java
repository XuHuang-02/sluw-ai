package com.sinosig.sluw.application.authority;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sinosig.sluw.application.commons.web.ConfigReader;
import com.sinosig.sluw.application.commons.web.SpringCtxUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Token鉴权拦截器
 * 拦截请求，提取Token，并回调全能保的安全验证接口进行鉴权。
 */
@Component
public class TokenAuthInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(TokenAuthInterceptor.class);

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 在控制器处理之前调用。进行Token验证。
     *
     * @param request  当前HTTP请求
     * @param response 当前HTTP响应
     * @param handler  被选中的处理器
     * @return 如果返回true，则继续执行后续拦截器和控制器；如果返回false，则中断流程。
     * @throws Exception 可能抛出异常
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 从请求中提取Token
        String token = extractTokenFromRequest(request);
        if (token == null || token.trim().isEmpty()) {
            logger.info("缺失访问令牌(Token)");
            sendErrorResponse(response, HttpStatus.UNAUTHORIZED.value(), "缺失访问令牌(Token)");
            return false;
        }

        // 2. 调用全能保验证接口的URL
        ConfigReader configReader = SpringCtxUtil.getSpringContext().getBean(ConfigReader.class);
        String securityUrl = configReader.getProperty("qnb", "security-url");
        String platform = configReader.getProperty("qnb", "platform");
        String url = String.format("%s?token=%s&platform=%s", securityUrl, token, platform);
        logger.info("调用全能保-URL: {}",url);
        ResponseEntity<String> authResponseEntity;
        try {
            // 3. 调用全能保系统的安全验证接口 (HTTP GET)
            authResponseEntity = restTemplate.getForEntity(url, String.class);
        } catch (Exception e) {
            // 调用认证服务失败（网络问题、服务不可用等）
            logger.info("认证服务调用失败: " + e.getMessage());
            sendErrorResponse(response, HttpStatus.INTERNAL_SERVER_ERROR.value(),
                    "认证服务调用失败: " + e.getMessage());
            return false;
        }

        // 4. 处理全能保返回的响应
        if (authResponseEntity.getStatusCode() == HttpStatus.OK && authResponseEntity.getBody() != null) {
            try {
                // 解析JSON响应
                Map<String, Object> resultMap = objectMapper.readValue(authResponseEntity.getBody(), Map.class);
                String resultCode = String.valueOf(resultMap.get("code")); // 使用String.valueOf避免null

                // 5. 根据全能保返回的code判断鉴权结果
                if ("200".equals(resultCode)) {
                    // 验证成功
                    // 将用户信息存入Session，供后续控制器使用
                    Object userInfo = resultMap.get("data"); // 根据platform不同，data结构可能不同
                    request.getSession().setAttribute("currentUserInfo", userInfo);
                    // 可选：记录日志
                    logger.info("用户Token验证通过: {}, URL: {}", userInfo, request.getRequestURI());

                    RedisTemplate<String, Object> redisTemplate = SpringCtxUtil.getSpringContext().getBean("redisTemplate", RedisTemplate.class);
                    String redisKey = "user:info:" + token;
                    ObjectMapper objectMapper = new ObjectMapper();
                    String userInfoJson = objectMapper.writeValueAsString(userInfo);
                    // 存储到 Redis，并设置过期时间（60分钟）
                    redisTemplate.opsForValue().set(redisKey, userInfoJson, 60, TimeUnit.MINUTES);

                    // 将token设置到Cookie中
                    Cookie userTokenCookie = new Cookie("USERINFO_TOKEN", token);
                    userTokenCookie.setPath("/");
                    userTokenCookie.setHttpOnly(true); // 防止XSS攻击
                    userTokenCookie.setMaxAge(60 * 60); // 60分钟，与Redis过期时间一致
                    userTokenCookie.setSecure(true); // HTTPS
                    response.addCookie(userTokenCookie);

                    // 重定向到index.html
                    //response.sendRedirect("/index.html");
                    //请求转发
                    request.getRequestDispatcher("/index.html").forward(request, response);

                } else {
                    // 验证失败
                    String errorMsg = (String) resultMap.getOrDefault("message", "Token验证失败");
                    logger.info("Token验证失败: " + errorMsg);
                    sendErrorResponse(response, HttpStatus.FORBIDDEN.value(), "验证失败: " + errorMsg);
                }
                return false;
            } catch (Exception e) {
                // 解析全能保响应失败
                sendErrorResponse(response, HttpStatus.INTERNAL_SERVER_ERROR.value(),
                        "解析认证服务响应失败: " + e.getMessage());
                return false;
            }
        } else {
            // 全能保返回了非200的HTTP状态码
            sendErrorResponse(response, authResponseEntity.getStatusCode().value(),
                    "认证服务返回异常状态: " + authResponseEntity.getStatusCode());
            return false;
        }
    }

    /**
     * 在控制器处理之后、视图渲染之前调用。（可选实现）
     */
    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler,
                           @Nullable ModelAndView modelAndView) throws Exception {
        // 可在此处进行模型数据的修改或记录，根据需要实现
    }

    /**
     * 在请求处理完成后调用（视图已渲染）。（可选实现）
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                @Nullable Exception ex) throws Exception {
        // 可在此处进行资源清理或记录请求完成日志，根据需要实现
    }

    /**
     * 从HTTP请求中提取Token。
     * 优先级：1. Authorization请求头 (Bearer Token)；2. URL查询参数 token。
     *
     * @param request HTTP请求
     * @return 提取到的Token，未找到则返回null
     */
    private String extractTokenFromRequest(HttpServletRequest request) {
        // 方式1：从标准 Authorization: Bearer <token> 头获取
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7); // 移除"Bearer "前缀
        }

        // 方式2：从URL查询参数获取 (例如: ?token=xxxxx)
        String tokenParam = request.getParameter("token");
        if (tokenParam != null && !tokenParam.trim().isEmpty()) {
            return tokenParam;
        }

        // 方式3：从特定Cookie中获取
        try {
            // 从统一配置中读取Cookie的名称
            ConfigReader configReader = SpringCtxUtil.getSpringContext().getBean(ConfigReader.class);
            String tokenCookieName = configReader.getProperty("qnb", "token-cookie-name");

            if (tokenCookieName != null && !tokenCookieName.trim().isEmpty()) {
                Cookie[] cookies = request.getCookies();
                if (cookies != null) {
                    for (Cookie cookie : cookies) {
                        if (tokenCookieName.equals(cookie.getName())) {
                            String tokenFromCookie = cookie.getValue();
                            if (tokenFromCookie != null && !tokenFromCookie.trim().isEmpty()) {
                                logger.debug("从Cookie: {} 中获取到Token", tokenCookieName);
                                return tokenFromCookie;
                            }
                        }
                    }
                }
                logger.info("未在请求中找到名为 [{}] 的Cookie，或Cookie值为空。", tokenCookieName);
            } else {
                logger.info("未配置用于获取Token的Cookie名称(token-cookie-name)，跳过从Cookie获取。");
            }
        } catch (Exception e) {
            // 获取配置或处理Cookie过程中发生异常，不应阻断主流程，仅记录日志。
            logger.error("从Cookie获取Token时发生异常，将视为Token不存在。异常信息: {}", e.getMessage());
        }

        return null;
    }

    /**
     * 发送标准化的错误JSON响应。
     *
     * @param response    HTTP响应对象
     * @param statusCode  HTTP状态码
     * @param message     错误信息
     * @throws IOException 可能抛出IO异常
     */
    private void sendErrorResponse(HttpServletResponse response, int statusCode, String message) throws IOException {
        response.setStatus(statusCode);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        // 构建错误信息JSON对象
        Map<String, Object> errorBody = Map.of(
                "timestamp", System.currentTimeMillis(),
                "status", statusCode,
                "error", HttpStatus.valueOf(statusCode).getReasonPhrase(),
                "message", message,
                "path", "" // 可以填入 request.getRequestURI()
        );

        String jsonResponse = objectMapper.writeValueAsString(errorBody);
        response.getWriter().write(jsonResponse);
        response.getWriter().flush();
    }
}