package com.sinosig.sluw.application.controller;

import com.sinosig.sluw.application.commons.web.SpringCtxUtil;
import jakarta.servlet.http.HttpSession;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.core.env.Environment;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*", maxAge = 3600)
public class AuthController {

    private final Environment environment;

    public AuthController(Environment environment) {
        this.environment = environment;
    }

    /**
     * 生成验证码接口
     */
    @GetMapping("/captcha")
    public ResponseEntity<Map<String, Object>> generateCaptcha(HttpSession session) {
        // 生成4位随机验证码（字母数字混合）
        String captcha = generateRandomCaptcha(4);

        // 获取会话ID作为key
        String sessionId = session.getId();
        RedisTemplate<String, Object> redisTemplate = SpringCtxUtil.getSpringContext().getBean("redisTemplate", RedisTemplate.class);
        String yzmKey = "login:yzm:" + sessionId;
        // 设置验证码过期时间（3分钟）
        redisTemplate.opsForValue().set(yzmKey, captcha, 3, TimeUnit.MINUTES);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("captcha", captcha);

        return ResponseEntity.ok(response);
    }

    /**
     * 登录接口
     */
    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestBody LoginRequest loginRequest,
            HttpSession session) {

        Map<String, Object> response = new HashMap<>();

        // 1. 验证验证码
        String sessionId = session.getId();
        RedisTemplate<String, Object> redisTemplate = SpringCtxUtil.getSpringContext().getBean("redisTemplate", RedisTemplate.class);
        String yzmKey = "login:yzm:" + sessionId;
        String yzmCaptcha = String.valueOf(redisTemplate.opsForValue().get(yzmKey));

        if (yzmCaptcha == null) {
            response.put("success", false);
            response.put("message", "验证码已过期，请刷新后重试");
            return ResponseEntity.badRequest().body(response);
        }

        if (!yzmCaptcha.equalsIgnoreCase(loginRequest.getCaptcha())) {
            response.put("success", false);
            response.put("message", "验证码不正确");
            return ResponseEntity.badRequest().body(response);
        }

        // 2. 验证用户名和密码
        String storedPassword = environment.getProperty("legacy.auth.users." + loginRequest.getUsername());
        if (storedPassword == null) {
            response.put("success", false);
            response.put("message", "用户名或密码错误");
            return ResponseEntity.badRequest().body(response);
        }

        if (!storedPassword.equals(loginRequest.getPassword())) {
            response.put("success", false);
            response.put("message", "用户名或密码错误");
            return ResponseEntity.badRequest().body(response);
        }

        // 3. 登录成功，创建会话，保存登录状态
        session.setAttribute("user", loginRequest.getUsername());
        session.setAttribute("loginTime", System.currentTimeMillis());
        //设置登录状态，有效时长两小时
        String stateKey = "login:state:" + sessionId;
        redisTemplate.opsForValue().set(stateKey, "1", 120, TimeUnit.MINUTES);

        // 4. 返回成功响应
        response.put("success", true);
        response.put("message", "登录成功");
        response.put("username", loginRequest.getUsername());
        response.put("token", session.getId());

        return ResponseEntity.ok(response);
    }

    /**
     * 退出登录接口
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(HttpSession session) {
        session.invalidate();

        String sessionId = session.getId();
        RedisTemplate<String, Object> redisTemplate = SpringCtxUtil.getSpringContext().getBean("redisTemplate", RedisTemplate.class);
        redisTemplate.delete("login:state:" + sessionId);

        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "退出成功");

        return ResponseEntity.ok(response);
    }

    /**
     * 检查登录状态接口
     */
    @GetMapping("/check")
    public ResponseEntity<Map<String, Object>> checkAuth(HttpSession session) {
        boolean isLogin = false;
        String username = (String) session.getAttribute("user");
        String sessionId = session.getId();
        RedisTemplate<String, Object> redisTemplate = SpringCtxUtil.getSpringContext().getBean("redisTemplate", RedisTemplate.class);
        String stateKey = "login:state:" + sessionId;
        String stateCaptcha = String.valueOf(redisTemplate.opsForValue().get(stateKey));
        if ("1".equals(stateCaptcha)){
            isLogin = true;
        }

        Map<String, Object> response = new HashMap<>();
        response.put("isAuthenticated", isLogin);
        response.put("username", username);

        return ResponseEntity.ok(response);
    }

    /**
     * 生成随机验证码
     */
    private String generateRandomCaptcha(int length) {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        Random random = new Random();
        StringBuilder captcha = new StringBuilder();

        for (int i = 0; i < length; i++) {
            captcha.append(chars.charAt(random.nextInt(chars.length())));
        }

        return captcha.toString();
    }

    /**
     * 登录请求DTO
     */
    public static class LoginRequest {
        private String username;
        private String password;
        private String captcha;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }

        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }

        public String getCaptcha() { return captcha; }
        public void setCaptcha(String captcha) { this.captcha = captcha; }

    }
}
