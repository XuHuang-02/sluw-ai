package com.sinosig.sluw.application.commons.web;

import com.sinosig.sluw.application.commons.utils.Strings;
import jakarta.annotation.Resource;
import org.springframework.core.env.Environment;


public class ConfigReader {
	
	public static final String PREDIX = "application.config";
	
	@Resource
	private Environment env;
	
	public String getProperty (String scope, String key) {
		key = Strings.join(new String[]{PREDIX, scope, key}, ".");
		return env.getProperty(key);
	}
	public String getRequiredProperty (String scope, String key) {
		key = Strings.join(new String[]{PREDIX, scope, key}, ".");
		return env.getRequiredProperty(key);
	}
	
	public String getProperty (String scope, String key, String defaultValue) {
		key = Strings.join(new String[]{PREDIX, scope, key}, ".");
		return env.getProperty(key, defaultValue);
	}
	
	public <T> T getProperty (String scope, String key, Class<T> targetType, T defaultValue) {
		key = Strings.join(new String[]{PREDIX, scope, key}, ".");
		return env.getProperty(key, targetType, defaultValue);
	}
	public <T> T getRequiredProperty (String scope, String key, Class<T> targetType, T defaultValue) {
		key = Strings.join(new String[]{PREDIX, scope, key}, ".");
		return env.getProperty(key, targetType, defaultValue);
	}

}
