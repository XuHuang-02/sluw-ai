package com.sinosig.sluw.application.commons.datasource.share;


import com.sinosig.sluw.application.commons.datasource.druid.MultiDruidDataSources;

public class DataSourceSelector {

    /**
     * 线程threadlocal
     */
    private static ThreadLocal<String> dbLookCxt = new ThreadLocal<>();

    public static final String _DEFAULT_DB = MultiDruidDataSources.PREFIX + ".default";

    public static String getDataSourceKey() {
        String db = dbLookCxt.get();
        if (db == null) {
            db = _DEFAULT_DB;
        }
        return db;
    }

    public static void select(String dbKey) {
        dbLookCxt.set(dbKey);
    }

    public static void remove() {
        dbLookCxt.remove();
    }

}
