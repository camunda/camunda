/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application;

import io.camunda.application.initializers.DefaultAuthenticationInitializer;
import io.camunda.application.initializers.WebappsConfigurationInitializer;
import io.camunda.application.listeners.ApplicationErrorListener;
import io.camunda.optimize.OptimizeModuleConfiguration;
import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringBootConfiguration;

@SpringBootConfiguration(proxyBeanMethods = false)
public class StandaloneOptimize {

  public static final String ACTUATOR_PORT_PROPERTY_KEY = "management.server.port";
  private static final Logger LOG = LoggerFactory.getLogger(StandaloneOptimize.class);
  private static final ConfigurationService CONFIGURATION_SERVICE =
      ConfigurationService.createDefault();

  public static void main(final String[] args) {
    // To ensure that debug logging performed using java.util.logging is routed into Log4j 2
    MainSupport.putSystemPropertyIfAbsent(
        "java.util.logging.manager", "org.apache.logging.log4j.jul.LogManager");

    // Workaround for https://github.com/spring-projects/spring-boot/issues/26627
    MainSupport.putSystemPropertyIfAbsent(
        "spring.config.location",
        "optional:classpath:/,optional:classpath:/config/,optional:file:./,optional:file:./config/");

    MainSupport.putSystemPropertyIfAbsent(
        "spring.banner.location", "classpath:/optimize-banner.txt");

    final var standaloneOptimizeApplication =
        MainSupport.createDefaultApplicationBuilder()
            .sources(OptimizeModuleConfiguration.class)
            .profiles(Profile.OPTIMIZE.getId(), Profile.STANDALONE.getId())
            .addCommandLineProperties(true)
            .properties(getDefaultProperties())
            .initializers(
                new DefaultAuthenticationInitializer(), new WebappsConfigurationInitializer())
            .listeners(new ApplicationErrorListener())
            .build(args);

    standaloneOptimizeApplication.run(args);
  }

  private static Map<String, Object> getDefaultProperties() {
    final HashMap<String, Object> properties = new HashMap<>();

    properties.put(ACTUATOR_PORT_PROPERTY_KEY, CONFIGURATION_SERVICE.getActuatorPort());

    return properties;
  }
}
