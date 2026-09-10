package com.sinosig.sluw.application.commons.datasource.druid;

import com.sinosig.sluw.application.commons.datasource.share.DruidDataSourcePreperties;
import com.sinosig.sluw.application.commons.utils.Strings;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.core.env.Environment;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;


@ConfigurationProperties(prefix = MultiDruidConfigurationProperties.DB_CONFIG_PATH)
public class MultiDruidConfigurationProperties implements InitializingBean {

    private static final Logger logger = LoggerFactory.getLogger(MultiDruidConfigurationProperties.class);

    //
    public static final String DB_CONFIG_PATH = "spring.datasource.others";

    public List<DruidDataSourcePreperties> other_datasources = new ArrayList<DruidDataSourcePreperties>();

    @Resource
    private Environment env;


    @Override
    public void afterPropertiesSet() throws Exception {
        String others = env.getProperty(DB_CONFIG_PATH + ".use");
        logger.info(DB_CONFIG_PATH + ".use: {}", others);
        if (Strings.isEmpty(others)) {
            return;
        }
        String[] dba = Strings.splitNoEmpty(others, ",");
        for (int i = 0; dba != null && i < dba.length; i++) {
            String prefix = DB_CONFIG_PATH + "." + dba[i] + ".";
            other_datasources.add(getProperties(dba[i], prefix));
        }
    }

    public List<DruidDataSourcePreperties> getOther_datasources() {
        return other_datasources;
    }


    private DruidDataSourcePreperties getProperties(String id, String prefix) {
        Properties props = new Properties();
        props.setProperty("id", id);

        String driverClassName = env.getProperty(prefix + "driverClassName");
        if (!Strings.isEmpty(driverClassName)) {
            props.setProperty("driverClassName", driverClassName);
        }
        String url = env.getProperty(prefix + "url");
        if (!Strings.isEmpty(url)) {
            props.setProperty("url", url);
        }
        String username = env.getProperty(prefix + "username");
        if (!Strings.isEmpty(username)) {
            props.setProperty("username", username);
        }
        String password = env.getProperty(prefix + "password");
        if (!Strings.isEmpty(password)) {
            props.setProperty("password", password);
        }
        String validationQuery = env.getProperty(prefix + "validationQuery");
        if (!Strings.isEmpty(validationQuery)) {
            props.setProperty("validationQuery", validationQuery);
        }
        String type = env.getProperty(prefix + "type");
        if (!Strings.isEmpty(type)) {
            props.setProperty("type", type);
        }
        String filters = env.getProperty(prefix + "filters");
        if (!Strings.isEmpty(filters)) {
            props.setProperty("filters", filters);
        }
        String testWhileIdle = env.getProperty(prefix + "testWhileIdle");
        if (!Strings.isEmpty(testWhileIdle)) {
            props.setProperty("testWhileIdle", testWhileIdle);
        }
        String testOnBorrow = env.getProperty(prefix + "testOnBorrow");
        if (!Strings.isEmpty(testOnBorrow)) {
            props.setProperty("testOnBorrow", testOnBorrow);
        }
        String testOnReturn = env.getProperty(prefix + "testOnReturn");
        if (!Strings.isEmpty(testOnReturn)) {
            props.setProperty("testOnReturn", testOnReturn);
        }
        String poolPreparedStatements = env.getProperty(prefix + "poolPreparedStatements");
        if (!Strings.isEmpty(poolPreparedStatements)) {
            props.setProperty("poolPreparedStatements", poolPreparedStatements);
        }
        String maxOpenPreparedStatements = env.getProperty(prefix + "maxOpenPreparedStatements");
        if (!Strings.isEmpty(maxOpenPreparedStatements)) {
            props.setProperty("maxOpenPreparedStatements", maxOpenPreparedStatements);
        }
        String maxActive = env.getProperty(prefix + "maxActive");
        if (!Strings.isEmpty(maxActive)) {
            props.setProperty("maxActive", maxActive);
        }
        String initialSize = env.getProperty(prefix + "initialSize");
        if (!Strings.isEmpty(initialSize)) {
            props.setProperty("initialSize", initialSize);
        }
        String maxWait = env.getProperty(prefix + "maxWait");
        if (!Strings.isEmpty(maxWait)) {
            props.setProperty("maxWait", maxWait);
        }
        String minIdle = env.getProperty(prefix + "minIdle");
        if (!Strings.isEmpty(minIdle)) {
            props.setProperty("minIdle", minIdle);
        }
        String timeBetweenEvictionRunsMillis = env.getProperty(prefix + "timeBetweenEvictionRunsMillis");
        if (!Strings.isEmpty(timeBetweenEvictionRunsMillis)) {
            props.setProperty("timeBetweenEvictionRunsMillis", timeBetweenEvictionRunsMillis);
        }
        String minEvictableIdleTimeMillis = env.getProperty(prefix + "minEvictableIdleTimeMillis");
        if (!Strings.isEmpty(minEvictableIdleTimeMillis)) {
            props.setProperty("minEvictableIdleTimeMillis", minEvictableIdleTimeMillis);
        }
        String encrypted = env.getProperty(prefix + "encrypted");
        if (!Strings.isEmpty(encrypted)) {
            props.setProperty("encrypted", encrypted);
        }
        String publicKey = env.getProperty(prefix + "publicKey");
        if (!Strings.isEmpty(publicKey)) {
            props.setProperty("publicKey", publicKey);
        }
        String privateKey = env.getProperty(prefix + "privateKey");
        if (!Strings.isEmpty(privateKey)) {
            props.setProperty("privateKey", privateKey);
        }
        return new DruidDataSourcePreperties(props);
    }


}
