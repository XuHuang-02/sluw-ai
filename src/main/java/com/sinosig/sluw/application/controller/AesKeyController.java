package com.sinosig.sluw.application.controller;

import com.sinosig.sluw.application.commons.web.ConfigReader;
import com.sinosig.sluw.application.commons.web.SpringCtxUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AesKeyController {


    @GetMapping("/api/key")
    public ResponseEntity<String> getAesKey() {
        ConfigReader configReader = SpringCtxUtil.getSpringContext().getBean(ConfigReader.class);
        String key = configReader.getProperty("aes", "key");
        // 返回密钥
        return ResponseEntity.ok(key);
    }
}
