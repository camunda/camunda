/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize;

import java.util.Map;
import java.util.Optional;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.stereotype.Component;

@Component
public class SpringPropertiesPostProcessor implements EnvironmentPostProcessor {

  private static final String PROPERTY_SOURCE_NAME = "defaultProperties";
  public static final String SPRING_HTTP2_ENABLED_PROPERTY = "server.http2.enabled";

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    Map<String, Object> propertiesToAddMap = createSpringProperties();
    addToDefaultProperties(environment.getPropertySources(), propertiesToAddMap);
  }

  private Map<String, Object> createSpringProperties() {
    ConfigurationService preAutowireConfigService = ConfigurationService.createDefault();
    return Map.of(
        SPRING_HTTP2_ENABLED_PROPERTY, preAutowireConfigService.getContainerHttp2Enabled());
  }

  /*
     This does NOT overwrite properties defined in application.properties
  */
  private void addToDefaultProperties(
      MutablePropertySources propertySources, Map<String, Object> propertiesToAddMap) {
    Optional.ofNullable(propertySources.get(PROPERTY_SOURCE_NAME))
        .filter(MapPropertySource.class::isInstance)
        .map(MapPropertySource.class::cast)
        .ifPresentOrElse(
            propertySource -> {
              for (String key : propertiesToAddMap.keySet()) {
                if (!propertySource.containsProperty(key)) {
                  propertySource.getSource().put(key, propertiesToAddMap.get(key));
                }
              }
            },
            () ->
                propertySources.addLast(
                    new MapPropertySource(PROPERTY_SOURCE_NAME, propertiesToAddMap)));
  }
}
