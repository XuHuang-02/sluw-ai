package com.sinosig.sluw.application.commons.datasource.share;

import com.alibaba.druid.filter.config.ConfigTools;
import com.alibaba.druid.pool.DruidDataSource;
import com.sinosig.sluw.application.commons.utils.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.SQLException;

public class DruidDataSourceCreator {

    private static final Logger logger = LoggerFactory.getLogger(DruidDataSourceCreator.class);

    public static DataSource dataSource(DruidDataSourcePreperties properties) {
        logger.info("create datasource '{}' --> {}", properties.getId(), properties.getUrl());
        DruidDataSource dataSource = new DruidDataSource();
        dataSource.setDriverClassName(properties.getDriverClassName());
        dataSource.setUrl(properties.getUrl());
        dataSource.setUsername(properties.getUsername());//用户名
        String password = properties.getPassword();
        String publicKey = properties.getPublicKey();
        if (properties.isEncrypted()) {
            try {
                if (Strings.isEmpty(publicKey)) {
                    password = ConfigTools.decrypt(password);
                } else {
                    password = ConfigTools.decrypt(publicKey, password);
                }
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
        dataSource.setPassword(password);//密码
        dataSource.setInitialSize(properties.getInitialSize());
        dataSource.setMaxActive(properties.getMaxActive());
        dataSource.setMinIdle(properties.getMinIdle());
        //dataSource.setMaxIdle(env.getProperty("spring.datasource.max-idle", Integer.class, 10));
        dataSource.setMaxWait(properties.getMaxWait());
        dataSource.setValidationQuery(properties.getValidationQuery());
        dataSource.setDriverClassName(properties.getDriverClassName());
        dataSource.setTestOnBorrow(properties.isTestOnBorrow());
        dataSource.setTestWhileIdle(properties.isTestWhileIdle());
        dataSource.setPoolPreparedStatements(properties.isPoolPreparedStatements());
        try {
            dataSource.setFilters("stat");
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return dataSource;
    }

    public static void main(String[] args) throws Exception {
        //System.out.println(ConfigTools.encrypt("wK8A8*pQ4"));
        System.out.println(ConfigTools.encrypt("slisoper"));
    }

}
