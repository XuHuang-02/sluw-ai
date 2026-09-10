package com.sinosig.sluw.application.commons.datasource.routable;

/**
 *
 * 路由查找接口定义
 *
 * @author Guosubin
 *
 */
public interface DBRoutable {

    /**
     * 获取路由数据库的key值
     *
     * @return
     */
    public String getRoutingKey() throws Exception;
}
