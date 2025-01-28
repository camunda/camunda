/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize;

import static io.camunda.optimize.tomcat.OptimizeResourceConstants.ACTUATOR_PORT_PROPERTY_KEY;

import io.camunda.optimize.service.util.configuration.ConfigurationService;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.freemarker.FreeMarkerAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication(exclude = {FreeMarkerAutoConfiguration.class})
@ComponentScan(excludeFilters = @ComponentScan.Filter(IgnoreDuringScan.class))
public class TestsEntry {
  // This is the application entry point used to launch tests.
  //
  // The reason why we need this is that the entry point StandaloneOptimize belongs to a package
  // that imports optimize-backend. This means that here (optimize-backend) we cannot import
  // that package, as it would generate a circular dependency.
  //
  // NOTE: This is a test class and it is not packaged within the production artifact.

  public static void main(final String[] args) {
    System.setProperty(
        "spring.web.resources.static-locations", "classpath:/META-INF/resources/optimize/");

    final SpringApplication optimize = new SpringApplication(TestsEntry.class);
    final ConfigurationService configurationService = ConfigurationService.createDefault();

    final Map<String, Object> defaultProperties = new HashMap<>();
    defaultProperties.put(ACTUATOR_PORT_PROPERTY_KEY, configurationService.getActuatorPort());
    defaultProperties.put("useLegacyPort", "true");
    optimize.setDefaultProperties(defaultProperties);

    optimize.run(args);
  }
}
