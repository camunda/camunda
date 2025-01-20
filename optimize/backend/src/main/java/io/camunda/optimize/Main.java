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
import java.util.Collections;
import org.slf4j.Logger;
import org.springframework.boot.SpringApplication;

// @ComponentScan(excludeFilters = @ComponentScan.Filter(IgnoreDuringScan.class))
// @SpringBootApplication(exclude = {FreeMarkerAutoConfiguration.class})
public class Main {

  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(Main.class);

  public static void main(final String[] args) {
    // Temporarily hardcoding this property until we merge into a singleapp
    System.setProperty(
        "spring.web.resources.static-locations", "classpath:/META-INF/resources/optimize/");
    final SpringApplication optimize = new SpringApplication(Main.class);

    final ConfigurationService configurationService = ConfigurationService.createDefault();
    optimize.setDefaultProperties(
        Collections.singletonMap(
            ACTUATOR_PORT_PROPERTY_KEY, configurationService.getActuatorPort()));

    optimize.run(args);
  }
}
