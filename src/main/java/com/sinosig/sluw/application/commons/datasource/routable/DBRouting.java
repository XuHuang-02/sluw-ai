package com.sinosig.sluw.application.commons.datasource.routable;

import java.lang.annotation.*;

@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
/**
 * 路由处理注解
 * 用在方法上，如果与“事务（@Transactional）”一起使用，将该注解放在前面
 *
 * @author Guosubin
 *
 */
public @interface DBRouting {


}
