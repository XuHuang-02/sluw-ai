package com.sinosig.sluw.application.commons.datasource.routable;

import com.sinosig.sluw.application.commons.datasource.dao.DAO;
import com.sinosig.sluw.application.commons.utils.Arrays;
import jakarta.annotation.Resource;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;


@Aspect
@Order(0)
public class DBRoutingAspect {

    public static final Logger logger = LoggerFactory.getLogger(DBRoutingAspect.class);

    @Pointcut("@annotation(routable.datasource.commons.com.sinosig.sluw.application.DBRouting)")
    public void handle() {
    }

    @Resource
    private DAO dao;

    @Before("handle()")
    public void handleRouting(JoinPoint jp) {
        try {
            Object[] args = jp.getArgs();
            Object object = Arrays.get(args, 0);
            if (object != null && object instanceof DBRoutable) {
                DBRoutable routable = (DBRoutable) object;
                dao.routing(routable);
            }
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        }
    }

}
