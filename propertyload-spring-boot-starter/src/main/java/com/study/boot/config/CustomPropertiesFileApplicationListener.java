package com.study.boot.config;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.config.ConfigFileApplicationListener;
import org.springframework.boot.context.event.ApplicationEnvironmentPreparedEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.event.SmartApplicationListener;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.support.PropertiesLoaderUtils;
import org.springframework.util.StringUtils;

public class CustomPropertiesFileApplicationListener implements SmartApplicationListener, Ordered {

	public static int DEFAULT_ORDER = ConfigFileApplicationListener.DEFAULT_ORDER + 5;// 优先级要高于ConfigFileApplicationListener.DEFAULT_ORDER
	private int order = DEFAULT_ORDER;

	private static final String DEFAULT_FILE_SUFFIX = ".properties";
	public static final String PROPERTY_SOURCE_NAME = "CustomProperties";
	private static final String DEFAULT_SEARCH_LOCATIONS = "classpath:/,classpath:/config/,file:./,file:./config/";

	static Logger logger = LoggerFactory.getLogger(CustomPropertiesFileApplicationListener.class);

	@Override
	public boolean supportsEventType(Class<? extends ApplicationEvent> eventType) {
		return ApplicationEnvironmentPreparedEvent.class.isAssignableFrom(eventType);
	}

	@Override
	public boolean supportsSourceType(Class<?> sourceType) {
		return true;
	}

	@Override
	public int getOrder() {
		return this.order;
	}

	@Override
	public void onApplicationEvent(ApplicationEvent event) {
		if (event instanceof ApplicationEnvironmentPreparedEvent) {
			onApplicationEnvironmentPreparedEvent((ApplicationEnvironmentPreparedEvent) event);
		}
	}

	private void onApplicationEnvironmentPreparedEvent(ApplicationEnvironmentPreparedEvent event) {
		new PropertiesLoader(event.getEnvironment(), null).load();
	}

	private class PropertiesLoader {
		private ResourceLoader resourceLoader;
		private ConfigurableEnvironment environment;

		public PropertiesLoader(ConfigurableEnvironment environment, ResourceLoader resourceLoader) {
			if (resourceLoader == null) {
				this.resourceLoader = new DefaultResourceLoader();
			} else {
				this.resourceLoader = resourceLoader;
			}
			this.environment = environment;
		}

		void load() {
			// config location
			String classPath = getBasePath();
			// PropertiesLoaderUtils
			Set<String> fileNames = new LinkedHashSet<>();
			searchAllFiles(fileNames, classPath);

			Set<String> fileLocations = getConfigLocation();

			fileLocations.forEach((location) -> {
				fileNames.forEach(name -> {
					String locationPath = location + name;
					doLoad(locationPath);
				});
			});
		}

		private void searchAllFiles(Set<String> files, String location) {
			File file = new File(location);

			doSearch(files, file);
		}

		private void doSearch(Set<String> filesSet, File file) {
			if (file.isDirectory()) {
				File[] files = file.listFiles();
				for (File f : files) {
					doSearch(filesSet, f);
				}
			} else if (file.getName().endsWith(DEFAULT_FILE_SUFFIX)) {
				filesSet.add(file.getName());
			}

		}

		private void doLoad(String location) {
			Resource resource = this.resourceLoader.getResource(location);
			if (resource == null || !resource.exists()) {
				if (CustomPropertiesFileApplicationListener.logger.isWarnEnabled()) {
					CustomPropertiesFileApplicationListener.logger.warn(" Skipped missing config " + location);
				}
				return;
			}
			CustomPropertiesFileApplicationListener.logger.warn(" Custome config Found:" + location);
			String filename = resource.getFilename();
			if (filename != null && filename.endsWith(DEFAULT_FILE_SUFFIX)) {
				try {
					Properties properties = PropertiesLoaderUtils.loadProperties(resource);
					PropertySource<?> propertySource = environment.getPropertySources().get(PROPERTY_SOURCE_NAME);
					if (propertySource != null) {
						// merge
						PropertiesPropertySource source = (PropertiesPropertySource) propertySource;
						source.getSource().putAll((Map) properties);
					} else {
						propertySource = new PropertiesPropertySource(PROPERTY_SOURCE_NAME, properties);
						this.environment.getPropertySources().addLast(propertySource);
					}

				} catch (IOException e) {
					e.printStackTrace();
					throw new RuntimeException(e);
				}
			} else {
				CustomPropertiesFileApplicationListener.logger.warn(" Skipped invalid config " + resource);
			}
		}

		private Set<String> getConfigLocation() {
			return StringUtils.commaDelimitedListToSet(DEFAULT_SEARCH_LOCATIONS);
		}

		private String getBasePath() {
			// classPath
			URL url = this.getClass().getClassLoader().getResource("");
			return url.getFile();
		}
	}

}
