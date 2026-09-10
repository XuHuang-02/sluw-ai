package com.sinosig.sluw.assessment;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("assessment")
public record AssessmentSettings(String dataDir, boolean simulation, String bootstrapUser,
        String bootstrapPassword, String bootstrapOrg, String ragUrl, String ragKey,
        String visionUrl, String visionKey, String modelUrl, String modelKey,
        String ssoUrl, String ssoKey, String chatUrl, int workers, int queueSize) {}
