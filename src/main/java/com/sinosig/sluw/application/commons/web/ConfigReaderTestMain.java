package com.sinosig.sluw.application.commons.web;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication(scanBasePackages = "com.sinosig.sluw.application")
public class ConfigReaderTestMain {

    public static void main(String[] args) {
        ConfigurableApplicationContext context = SpringApplication.run(ConfigReaderTestMain.class, args);
        ConfigReader configReader = context.getBean(ConfigReader.class);

        // 测试读取单值
        String apiUrl = configReader.getProperty("ragFlow.base", "apiUrl");
        System.out.println("apiUrl = " + apiUrl);

        String datasetId = configReader.getProperty("ragFlow.dataregular", "datasetId");
        System.out.println("datasetId = " + datasetId);

        Double similarity = configReader.getProperty("ragFlow.dataregular", "vectorSimilarityWeight", Double.class, 0.0);
        System.out.println("similarityThreshold = " + similarity);

        Boolean keyword = configReader.getProperty("ragFlow.dataregular", "keyword", Boolean.class, false);
        System.out.println("keyword = " + keyword);

        context.close();
    }
}
