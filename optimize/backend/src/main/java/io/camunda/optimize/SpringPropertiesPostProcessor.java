/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize;

import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.stereotype.Component;

@Component
public class SpringPropertiesPostProcessor implements EnvironmentPostProcessor {

  public static final String SPRING_HTTP2_ENABLED_PROPERTY = "server.http2.enabled";
  private static final String PROPERTY_SOURCE_NAME = "defaultProperties";

  @Override
  public void postProcessEnvironment(
      final ConfigurableEnvironment environment, final SpringApplication application) {
    final Map<String, Object> propertiesToAddMap = createSpringProperties();
    addToDefaultProperties(environment.getPropertySources(), propertiesToAddMap);
  }

  private Map<String, Object> createSpringProperties() {
    final ConfigurationService preAutowireConfigService = ConfigurationService.createDefault();
    return Map.of(
        SPRING_HTTP2_ENABLED_PROPERTY, preAutowireConfigService.getContainerHttp2Enabled());
  }

  /*
     This does NOT overwrite properties defined in application.properties
  */
  private void addToDefaultProperties(
      final MutablePropertySources propertySources, final Map<String, Object> propertiesToAddMap) {
    Optional.ofNullable(propertySources.get(PROPERTY_SOURCE_NAME))
        .filter(MapPropertySource.class::isInstance)
        .map(MapPropertySource.class::cast)
        .ifPresentOrElse(
            propertySource -> {
              for (final String key : propertiesToAddMap.keySet()) {
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
