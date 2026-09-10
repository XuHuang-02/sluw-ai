package com.sinosig.sluw.application.commons.datasource.druid;

import com.sinosig.sluw.application.commons.datasource.dao.DaoImpl;
import com.sinosig.sluw.application.commons.datasource.routable.DBRoutingAspect;
import jakarta.annotation.Resource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;


@Configuration // 配置注解  
@EnableConfigurationProperties({DefaultDruidConfigurationProperties.class, MultiDruidConfigurationProperties.class})
// 开启指定类的配置
@ConditionalOnProperty(prefix = MultiDruidDataSources.PREFIX, name = "active", matchIfMissing = false)// 指定的属性是否有指定的值
public class MultiDruidDataSourcesAutoConfiguration {

    @Resource
    private DefaultDruidConfigurationProperties defaultDruidConfigurationProperties;

    @Resource
    private MultiDruidConfigurationProperties multiDruidConfigurationProperties;

    @Bean()
    public DataSource dataSource() {
        return new MultiDruidDataSources(defaultDruidConfigurationProperties, multiDruidConfigurationProperties);
    }

    @Bean
    public DaoImpl daoImpl() {
        return new DaoImpl();
    }

    @Bean
    public DBRoutingAspect dbRoutingAspect() {
        return new DBRoutingAspect();
    }
}
