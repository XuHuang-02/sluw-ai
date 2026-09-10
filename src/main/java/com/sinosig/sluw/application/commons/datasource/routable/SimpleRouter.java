package com.sinosig.sluw.application.commons.datasource.routable;

/**
 *
 * 直接路由模式
 *
 * @author Guosubin
 *
 */
public class SimpleRouter implements DBRoutable {


    private String dbKey;

    public SimpleRouter(String dbKey) {
        this.dbKey = dbKey;
    }

    @Override
    public String getRoutingKey() throws Exception {
        return dbKey;
    }

}
