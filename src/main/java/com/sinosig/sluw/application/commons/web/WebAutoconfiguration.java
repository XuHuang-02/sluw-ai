package com.sinosig.sluw.application.commons.web;

import jakarta.annotation.Resource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class WebAutoconfiguration {

	@Resource
	private Environment env;

	@Bean
	public ConfigReader configReader(){
		return new ConfigReader();
	}
	
	@Bean
	public SpringCtxUtil springCtxUtil(){
		return new SpringCtxUtil();
	}
	


}