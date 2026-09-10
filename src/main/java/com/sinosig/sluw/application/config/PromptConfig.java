package com.sinosig.sluw.application.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 提示词配置类
 * 用于从application.yml中加载提示词模板
 *
 * @author
 * @version 1.0
 */
@Configuration
@ConfigurationProperties(prefix = "spring.ai.prompts")
public class PromptConfig {

    /**
     * 问题润色提示词模板
     */
    private PromptTemplate questionRefiner;

    /**
     * 最终总结提示词模板
     */
    private PromptTemplate finalSummarizer;

    /**
     * 获取问题润色提示词模板
     * @return PromptTemplate对象
     */
    public PromptTemplate getQuestionRefiner() {
        return questionRefiner;
    }

    /**
     * 设置问题润色提示词模板
     * @param questionRefiner PromptTemplate对象
     */
    public void setQuestionRefiner(PromptTemplate questionRefiner) {
        this.questionRefiner = questionRefiner;
    }

    /**
     * 获取最终总结提示词模板
     * @return PromptTemplate对象
     */
    public PromptTemplate getFinalSummarizer() {
        return finalSummarizer;
    }

    /**
     * 设置最终总结提示词模板
     * @param finalSummarizer PromptTemplate对象
     */
    public void setFinalSummarizer(PromptTemplate finalSummarizer) {
        this.finalSummarizer = finalSummarizer;
    }

    /**
     * 提示词模板内部类
     */
    public static class PromptTemplate {

        /**
         * 提示词模板内容
         */
        private String template;

        /**
         * 获取提示词模板内容
         * @return 模板字符串
         */
        public String getTemplate() {
            return template;
        }

        /**
         * 设置提示词模板内容
         * @param template 模板字符串
         */
        public void setTemplate(String template) {
            this.template = template;
        }
    }
}

