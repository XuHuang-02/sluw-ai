package com.sinosig.sluw.application.commons.datasource.dao;

import com.sinosig.sluw.application.commons.datasource.routable.DBRoutable;

import java.util.List;

/**
 * 数据层操作接口
 *
 * @author Guosubin
 *
 */
public interface DAO {

    /**
     * 数据查询>>>string
     *
     * @param sqlId
     * @return
     * @throws DaoException
     */
    public String getOneValue(String sqlId) throws DaoException;

    /**
     * 数据查询<<< object/map/list
     * >>> string
     *
     * @param sqlId
     * @param obj
     * @return
     * @throws DaoException
     */
    public String getOneValue(String sqlId, Object obj) throws DaoException;


    /**
     * 数据查询>>> object/map
     *
     * @param sqlId
     * @return
     * @throws DaoException
     */
    public <T> T get(String sqlId) throws DaoException;

    /**
     * 数据查询<<< object/map/list
     * >>> object/map
     *
     * @param sqlId
     * @param obj
     * @return
     * @throws DaoException
     */
    public <T> T get(String sqlId, Object obj) throws DaoException;

    /**
     * 数据查询(依主键)<<< entity
     * >>> object/map
     *
     * @param entity - 实体类
     * @return
     * @throws DaoException
     */
    public <E> E get(E entity) throws DaoException;


    /**
     * 数据查询<<< object/map/list
     * >>> list
     *
     * @param sqlId
     * @return
     * @throws DaoException
     */
    public <E> List<E> list(String sqlId) throws DaoException;

    /**
     * 数据查询<<< object/map/list
     * >>> list
     *
     * @param sqlId
     * @param obj
     * @return
     * @throws DaoException
     */
    public <E> List<E> list(String sqlId, Object obj) throws DaoException;

    /**
     * 数据查询(依条件)<<< entity
     * >>> list
     *
     * @param entity - 实体类
     * @return
     * @throws DaoException
     */
    public <E> List<E> list(E entity) throws DaoException;

    /**
     * 路由选择
     *
     * @param routable
     * @throws Exception
     */
    public void routing(DBRoutable routable) throws Exception;


	/**
	 * 数据保存>>> int
	 * @param sqlId
	 * @return number of insert
	 * @throws DaoException
	 */
	public int save(String sqlId) throws DaoException;

	/**
	 * 数据保存 <<< object/map/list
	 * 		   >>> int
	 * @param sqlId
	 * @param obj
	 * @return number of insert
	 * @throws DaoException
	 */
	public int save(String sqlId, Object obj) throws DaoException;

	/**
	 * 数据保存 <<< entity
	 * 		   >>> int
	 * @param sqlId - generated with : mybatis.generated.${ObjectName}.replace('Entity','Mapper');
	 * @param entity - 实体类
	 * @return number of insert
	 * @throws DaoException
	 */
	public <E> int save(E entity) throws DaoException;

	/**
	 * 数据更新
	 * 		  >>> int
	 * @param sqlId
	 * @return
	 * @throws DaoException
	 */
	public int update(String sqlId) throws DaoException;
	/**
	 * 数据更新 <<< object/map/list
	 * 		   >>> int
	 * @param sqlId
	 * @param obj
	 * @return
	 * @throws DaoException
	 */
	public int update(String sqlId, Object obj) throws DaoException;
	/**
	 * 数据更新(依主键)(仅仅更新非空的字段) <<< entity
	 * 		   >>> int
	 * @param sqlId - generated with : mybatis.generated.${ObjectName}.replace('Entity','Mapper');
	 * @param entity - 实体类
	 * @return
	 * @throws DaoException
	 */
	public <E> int update(E entity) throws DaoException;
	/**
	 * 数据更新(依主键 - FORCE)(无限制的更新所有字段) <<< entity
	 * 		   >>> int
	 * @param sqlId - generated with : mybatis.generated.${ObjectName}.replace('Entity','Mapper');
	 * @param entity - 实体类
	 * @return
	 * @throws DaoException
	 */
	public <E> int Update(E entity) throws DaoException;
}
