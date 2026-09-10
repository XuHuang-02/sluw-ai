package com.sinosig.sluw.application.commons.web;

import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;


public class SpringCtxUtil implements ApplicationContextAware {

	private static ApplicationContext ctx;
	
	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		ctx = applicationContext;
	}

	public static ApplicationContext getSpringContext(){
		return ctx;
	}
	
	public <T> T getBean(Class<T> clazz) {
		return ctx.getBean(clazz);
	}
}

