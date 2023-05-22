/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.freemarker.FreeMarkerAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;

import java.util.Collections;

import static org.camunda.optimize.jetty.OptimizeResourceConstants.ACTUATOR_PORT_PROPERTY_KEY;

@Slf4j
@ComponentScan(excludeFilters = @ComponentScan.Filter(IgnoreDuringScan.class))
@SpringBootApplication(exclude = {FreeMarkerAutoConfiguration.class})
public class Main {
  public static void main(String[] args) {
    SpringApplication optimize = new SpringApplication(Main.class);

    final ConfigurationService configurationService = ConfigurationService.createDefault();
    optimize.setDefaultProperties(Collections.singletonMap(ACTUATOR_PORT_PROPERTY_KEY, configurationService.getActuatorPort()));

    optimize.run(args);
  }
}
