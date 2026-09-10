package com.sinosig.sluw.application;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SluwAssistantApplication {
    public static void main(String[] args) {
        SpringApplication.run(SluwAssistantApplication.class, args);
    }
//    public static void main(String[] args) {
//        ConfigurableApplicationContext context = SpringApplication.run(SluwAssistantApplication.class, args);
//        ExcelTestService service = context.getBean(ExcelTestService.class);
//        String result = service.processLocalFile();
//        System.out.println("最终结果：\n" + result);
//        // 可以关闭上下文或保持运行
//    }
}