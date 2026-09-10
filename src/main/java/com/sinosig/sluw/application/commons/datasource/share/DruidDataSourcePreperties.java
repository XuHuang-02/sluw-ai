package com.sinosig.sluw.application.commons.datasource.share;

import com.sinosig.sluw.application.commons.utils.Strings;

import java.util.Properties;


public class DruidDataSourcePreperties {

    protected String id;

    protected boolean initialized;

    protected String driverClassName = "";
    protected String url = "";
    protected String username = "";
    protected String password = "";
    protected String type = "com.alibaba.druid.pool.DruidDataSource";
    protected String filters = "stat";
    protected int maxActive = 20;
    protected int initialSize = 1;
    protected int maxWait = 60000;
    protected int minIdle = 1;
    protected int timeBetweenEvictionRunsMillis = 60000;
    protected int minEvictableIdleTimeMillis = 300000;
    protected String validationQuery = "";
    protected boolean testWhileIdle = true;
    protected boolean testOnBorrow = false;
    protected boolean testOnReturn = false;
    protected boolean poolPreparedStatements = true;
    protected int maxOpenPreparedStatements = 20;
    protected boolean encrypted = true;
    protected String publicKey = null;
    protected String privateKey = null;


    public DruidDataSourcePreperties() {

    }

    public DruidDataSourcePreperties(String id) {
        this.id = id;
    }

    public DruidDataSourcePreperties(Properties props) {
        initProperties(props);
    }

    public void initProperties(Properties props) {

        id = props.getProperty("id", id);
        driverClassName = props.getProperty("driverClassName", driverClassName);
        url = props.getProperty("url", url);
        username = props.getProperty("username", username);
        password = props.getProperty("password", password);
        validationQuery = props.getProperty("validationQuery", validationQuery);

        type = props.getProperty("type", "com.alibaba.druid.pool.DruidDataSource");
        filters = props.getProperty("filters", "stat");

        testWhileIdle = Strings.toBoolean(props.getProperty("testWhileIdle", "true"));
        testOnBorrow = Strings.toBoolean(props.getProperty("testOnBorrow", "false"));
        testOnReturn = Strings.toBoolean(props.getProperty("testOnReturn", "false"));
        poolPreparedStatements = Strings.toBoolean(props.getProperty("poolPreparedStatements", "true"));

        maxOpenPreparedStatements = Strings.toInteger(props.getProperty("maxOpenPreparedStatements", "20"));
        maxActive = Strings.toInteger(props.getProperty("maxActive", "20"));
        initialSize = Strings.toInteger(props.getProperty("initialSize", "1"));
        maxWait = Strings.toInteger(props.getProperty("maxWait", "60000"));
        minIdle = Strings.toInteger(props.getProperty("minIdle", "1"));
        timeBetweenEvictionRunsMillis = Strings.toInteger(props.getProperty("timeBetweenEvictionRunsMillis", "60000"));
        minEvictableIdleTimeMillis = Strings.toInteger(props.getProperty("minEvictableIdleTimeMillis", "300000"));


        //
        publicKey = props.getProperty("publicKey", "");
        privateKey = props.getProperty("privateKey", "");
        encrypted = Strings.toBoolean(props.getProperty("encrypted", "true"));


    }

    public Properties getProperties() {
        Properties props = new Properties();
        props.setProperty("id", Strings.nvl(id));
        props.setProperty("initialized", String.valueOf(initialized));
        props.setProperty("driverClassName", Strings.nvl(driverClassName));
        props.setProperty("url", Strings.nvl(url));
        props.setProperty("username", Strings.nvl(username));
        props.setProperty("password", Strings.nvl(password));
        props.setProperty("type", Strings.nvl(type));
        props.setProperty("filters", Strings.nvl(filters));
        props.setProperty("maxActive", String.valueOf(maxActive));
        props.setProperty("initialSize", String.valueOf(initialSize));
        props.setProperty("maxWait", String.valueOf(maxWait));
        props.setProperty("minIdle", String.valueOf(minIdle));
        props.setProperty("timeBetweenEvictionRunsMillis", String.valueOf(timeBetweenEvictionRunsMillis));
        props.setProperty("minEvictableIdleTimeMillis", String.valueOf(minEvictableIdleTimeMillis));
        props.setProperty("validationQuery", String.valueOf(validationQuery));
        props.setProperty("testWhileIdle", String.valueOf(testWhileIdle));
        props.setProperty("testOnBorrow", String.valueOf(testOnBorrow));
        props.setProperty("testOnReturn", String.valueOf(testOnReturn));
        props.setProperty("poolPreparedStatements", String.valueOf(poolPreparedStatements));
        props.setProperty("maxOpenPreparedStatements", String.valueOf(maxOpenPreparedStatements));
        //
        props.setProperty("publicKey", Strings.nvl(publicKey));
        props.setProperty("privateKey", Strings.nvl(privateKey));
        props.setProperty("encrypted", String.valueOf(encrypted));
        return props;
    }

    public void initAndCheck() {
        if (Strings.isEmpty(id)) {
            throw new RuntimeException("id is empty");
        }
        if (Strings.isEmpty(url)) {
            throw new RuntimeException("url is empty");
        }
        if (Strings.isEmpty(username)) {
            throw new RuntimeException("username is empty");
        }
        if (Strings.isEmpty(driverClassName)) {
            if (url.startsWith("jdbc:mysql")) {
                driverClassName = "com.mysql.jdbc.Driver";
            } else if (url.startsWith("jdbc:oracle")) {
                driverClassName = "oracle.jdbc.driver.OracleDriver";
            } else if (url.startsWith("jdbc:oceanbase")) {
                driverClassName = "com.alipay.oceanbase.jdbc.Driver";
            }
        }
        if (Strings.isEmpty(validationQuery)) {
            if (url.startsWith("jdbc:mysql")) {
                validationQuery = "SELECT 1";
            } else if (url.startsWith("jdbc:oracle")) {
                validationQuery = "SELECT 1 FROM DUAL";
            } else if (url.startsWith("jdbc:oceanbase")) {
                validationQuery = "SELECT 1";
            }
        }
        if (Strings.isEmpty(driverClassName)) {
            throw new RuntimeException("driverClassName is empty");
        }
        if (Strings.isEmpty(validationQuery)) {
            throw new RuntimeException("validationQuery is empty");
        }

    }


    public boolean isInitialized() {
        return initialized;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDriverClassName() {
        return driverClassName;
    }

    public void setDriverClassName(String driverClassName) {
        this.driverClassName = driverClassName;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getFilters() {
        return filters;
    }

    public void setFilters(String filters) {
        this.filters = filters;
    }

    public int getMaxActive() {
        return maxActive;
    }

    public void setMaxActive(int maxActive) {
        this.maxActive = maxActive;
    }

    public int getInitialSize() {
        return initialSize;
    }

    public void setInitialSize(int initialSize) {
        this.initialSize = initialSize;
    }

    public int getMaxWait() {
        return maxWait;
    }

    public void setMaxWait(int maxWait) {
        this.maxWait = maxWait;
    }

    public int getMinIdle() {
        return minIdle;
    }

    public void setMinIdle(int minIdle) {
        this.minIdle = minIdle;
    }

    public int getTimeBetweenEvictionRunsMillis() {
        return timeBetweenEvictionRunsMillis;
    }

    public void setTimeBetweenEvictionRunsMillis(int timeBetweenEvictionRunsMillis) {
        this.timeBetweenEvictionRunsMillis = timeBetweenEvictionRunsMillis;
    }

    public int getMinEvictableIdleTimeMillis() {
        return minEvictableIdleTimeMillis;
    }

    public void setMinEvictableIdleTimeMillis(int minEvictableIdleTimeMillis) {
        this.minEvictableIdleTimeMillis = minEvictableIdleTimeMillis;
    }

    public String getValidationQuery() {
        return validationQuery;
    }

    public void setValidationQuery(String validationQuery) {
        this.validationQuery = validationQuery;
    }

    public boolean isTestWhileIdle() {
        return testWhileIdle;
    }

    public void setTestWhileIdle(boolean testWhileIdle) {
        this.testWhileIdle = testWhileIdle;
    }

    public boolean isTestOnBorrow() {
        return testOnBorrow;
    }

    public void setTestOnBorrow(boolean testOnBorrow) {
        this.testOnBorrow = testOnBorrow;
    }

    public boolean isTestOnReturn() {
        return testOnReturn;
    }

    public void setTestOnReturn(boolean testOnReturn) {
        this.testOnReturn = testOnReturn;
    }

    public boolean isPoolPreparedStatements() {
        return poolPreparedStatements;
    }

    public void setPoolPreparedStatements(boolean poolPreparedStatements) {
        this.poolPreparedStatements = poolPreparedStatements;
    }

    public int getMaxOpenPreparedStatements() {
        return maxOpenPreparedStatements;
    }

    public void setMaxOpenPreparedStatements(int maxOpenPreparedStatements) {
        this.maxOpenPreparedStatements = maxOpenPreparedStatements;
    }

    public boolean isEncrypted() {
        return encrypted;
    }

    public void setEncrypted(boolean encrypted) {
        this.encrypted = encrypted;
    }

    public String getPublicKey() {
        return publicKey;
    }

    public void setPublicKey(String publicKey) {
        this.publicKey = publicKey;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }


}
