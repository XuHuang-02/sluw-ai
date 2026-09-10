package com.sinosig.sluw.application.commons.datasource.dao;

import com.sinosig.sluw.application.commons.datasource.routable.DBRoutable;
import com.sinosig.sluw.application.commons.datasource.share.DataSourceSelector;
import com.sinosig.sluw.application.commons.utils.Strings;
import jakarta.annotation.Resource;
import org.mybatis.spring.SqlSessionTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 *
 * @author Guosubin
 *
 */
@Repository
public class DaoImpl implements DAO {

    public static final Logger logger = LoggerFactory.getLogger(DaoImpl.class);

    @Resource(name = "sqlSessionTemplate")
    private SqlSessionTemplate sqlSessionTemplate;


    /**
     * {@inheritDoc}
     */
    @Override
    public String getOneValue(String sqlId) throws DaoException {
        try {
            routingDefault();
            return sqlSessionTemplate.selectOne(sqlId);
        } catch (Throwable e) {
            throw new DaoException(e.getMessage(), e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getOneValue(String sqlId, Object obj) throws DaoException {
        try {
            routingDefault();
            return sqlSessionTemplate.selectOne(sqlId, obj);
        } catch (Throwable e) {
            throw new DaoException(e.getMessage(), e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T get(String sqlId) throws DaoException {
        try {
            routingDefault();
            return sqlSessionTemplate.selectOne(sqlId);
        } catch (Throwable e) {
            throw new DaoException(e.getMessage(), e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <T> T get(String sqlId, Object obj) throws DaoException {
        try {
            routingDefault();
            return sqlSessionTemplate.selectOne(sqlId, obj);
        } catch (Throwable e) {
            throw new DaoException(e.getMessage(), e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <E> List<E> list(String sqlId) throws DaoException {
        try {
            routingDefault();
            return sqlSessionTemplate.selectList(sqlId);
        } catch (Throwable e) {
            throw new DaoException(e.getMessage(), e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <E> List<E> list(String sqlId, Object obj) throws DaoException {
        try {
            routingDefault();
            return sqlSessionTemplate.selectList(sqlId, obj);
        } catch (Throwable e) {
            throw new DaoException(e.getMessage(), e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <E> E get(E obj) throws DaoException {
        return get(getSqlId(obj, "get"), obj);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public <E> List<E> list(E obj) throws DaoException {
        return list(getSqlId(obj, "list"), obj);
    }

    /**
     * {@inheritDoc}
     */


    private String getSqlId(Object obj, String method) {
        return Strings.join(new String[]{"mybatis.generated.", obj.getClass().getSimpleName().replace("Entity", "Mapper"), ".", method}, "");
    }

    public void routing(DBRoutable routable) throws Exception {
        String routingKey = routable.getRoutingKey();
        logger.debug("routingKey --> {}", routingKey);
        DataSourceSelector.select(routingKey);
    }

    public void routingDefault() throws Exception {
        DataSourceSelector.remove();
        logger.debug("routingKey --> removed");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int save(String sqlId) throws DaoException {
        try {
            routingDefault();
            return sqlSessionTemplate.insert(sqlId);
        } catch (Throwable e) {
            throw new DaoException(e.getMessage(), e);
        }
    }
    /**
     * {@inheritDoc}
     */
    @Override
    public int save(String sqlId, Object obj) throws DaoException {
        try {
            routingDefault();
            return sqlSessionTemplate.insert(sqlId, obj);
        } catch (Throwable e) {
            throw new DaoException(e.getMessage(), e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int save(Object obj) throws DaoException {
        return save(getSqlId(obj, "save"), obj);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int update(String sqlId) throws DaoException {
        try {
            routingDefault();
            return sqlSessionTemplate.update(sqlId);
        } catch (Throwable e) {
            throw new DaoException(e.getMessage(), e);
        }
    }
    /**
     * {@inheritDoc}
     */
    @Override
    public int update(String sqlId, Object obj) throws DaoException {
        try {
            routingDefault();
            return sqlSessionTemplate.update(sqlId, obj);
        } catch (Throwable e) {
            throw new DaoException(e.getMessage(), e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int update(Object obj) throws DaoException {
        return update(getSqlId(obj, "update"), obj);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int Update(Object obj) throws DaoException {
        return update(getSqlId(obj, "Update"), obj);
    }

}


