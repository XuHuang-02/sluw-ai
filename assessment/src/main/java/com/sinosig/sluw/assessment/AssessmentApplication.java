package com.sinosig.sluw.assessment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(AssessmentSettings.class)
public class AssessmentApplication {
    public static void main(String[] args) { SpringApplication.run(AssessmentApplication.class, args); }
}
