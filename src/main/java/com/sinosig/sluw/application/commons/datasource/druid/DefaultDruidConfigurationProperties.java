package com.sinosig.sluw.application.commons.datasource.druid;

import com.sinosig.sluw.application.commons.datasource.share.DruidDataSourcePreperties;
import org.springframework.boot.context.properties.ConfigurationProperties;


@ConfigurationProperties(prefix = DefaultDruidConfigurationProperties.DB_CONFIG_PATH)
public class DefaultDruidConfigurationProperties extends DruidDataSourcePreperties {

    public static final String DB_CONFIG_PATH = "spring.datasource.default";

    public DefaultDruidConfigurationProperties() {
        this.id = "default";
    }


}
