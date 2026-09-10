package com.sinosig.sluw.application.commons.datasource.druid;

import com.sinosig.sluw.application.commons.datasource.share.DataSourceSelector;
import com.sinosig.sluw.application.commons.datasource.share.DruidDataSourceCreator;
import com.sinosig.sluw.application.commons.datasource.share.DruidDataSourcePreperties;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;


public class MultiDruidDataSources extends AbstractRoutingDataSource implements ApplicationContextAware {

    public static final String PREFIX = "spring.datasource";

    private String bean_prefix = PREFIX;

    private ApplicationContext cxt;

    private final Hashtable<String, DruidDataSourcePreperties> dsps = new Hashtable<String, DruidDataSourcePreperties>();

    private DefaultDruidConfigurationProperties defaultDruidConfigurationProperties;

    private MultiDruidConfigurationProperties multiDruidConfigurationProperties;

    public MultiDruidDataSources(DefaultDruidConfigurationProperties defaultDruidConfigurationProperties, MultiDruidConfigurationProperties multiDruidConfigurationProperties) {
        this.defaultDruidConfigurationProperties = defaultDruidConfigurationProperties;
        this.multiDruidConfigurationProperties = multiDruidConfigurationProperties;
    }


    public void afterPropertiesSet() {
        defineDefault();
        defineOthers();
        super.afterPropertiesSet();
    }

    private void defineDefault() {
        loadDBProperties(defaultDruidConfigurationProperties);
        initDataSource(defaultDruidConfigurationProperties.getId());
        this.setDefaultTargetDataSource(getDataSource(defaultDruidConfigurationProperties.getId()));
    }

    public void defineOthers() {
        List<DruidDataSourcePreperties> other_datasources = multiDruidConfigurationProperties.getOther_datasources();
        HashMap<Object, Object> datasources = new HashMap<Object, Object>();
        for (int i = 0; i < other_datasources.size(); i++) {
            loadDBProperties(other_datasources.get(i));
            initDataSource(other_datasources.get(i).getId());
            datasources.put(other_datasources.get(i).getId(), getDataSource(other_datasources.get(i).getId()));
        }
        this.setTargetDataSources(datasources);
    }

    public void setBeanPrefix(String beanPrefix) {
        bean_prefix = beanPrefix;
    }

    public String getBeanPrefix() {
        return bean_prefix;
    }

    @Override
    protected Object determineCurrentLookupKey() {
        return DataSourceSelector.getDataSourceKey();
    }

    public DataSource getDataSource(String DB_ID) {
        return cxt.getBean(bean_prefix + "." + DB_ID, DataSource.class);
    }

    private void loadDBProperties(DruidDataSourcePreperties druidDataSourcePreperties) {
        if (dsps.containsKey(druidDataSourcePreperties.getId())) {
            throw new RuntimeException("db '" + druidDataSourcePreperties.getId() + "' properties already exists");
        }
        druidDataSourcePreperties.initAndCheck();
        dsps.put(druidDataSourcePreperties.getId(), druidDataSourcePreperties);
    }

    private void initDataSource(String DB_ID) {
        if (!dsps.containsKey(DB_ID)) {
            throw new RuntimeException("db '" + DB_ID + "' properties has not loaded");
        }
        DruidDataSourcePreperties dataSourcePreperties = dsps.get(DB_ID);
        if (dataSourcePreperties.isInitialized()) {
            throw new RuntimeException("db '" + DB_ID + "' is initialized");
        }
        ConfigurableApplicationContext configurableApplicationContext = (ConfigurableApplicationContext) cxt;
        DefaultListableBeanFactory defaultListableBeanFactory = (DefaultListableBeanFactory) configurableApplicationContext.getBeanFactory();
        //创建数据源配置bean定义
        BeanDefinitionBuilder beanDefinitionBuilder = BeanDefinitionBuilder.genericBeanDefinition(DruidDataSourceCreator.class);
        beanDefinitionBuilder.addConstructorArgValue(dataSourcePreperties);
        beanDefinitionBuilder.setFactoryMethod("dataSource");
        defaultListableBeanFactory.registerBeanDefinition(bean_prefix + "." + DB_ID, beanDefinitionBuilder.getRawBeanDefinition());
        dataSourcePreperties.setInitialized(true);
    }


    @Override
    public void setApplicationContext(ApplicationContext cxt) throws BeansException {
        this.cxt = cxt;
    }
}
