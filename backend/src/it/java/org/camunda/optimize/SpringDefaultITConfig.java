/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize;

import org.camunda.optimize.service.util.configuration.ConfigurationService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

import static org.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder.createConfigurationFromLocations;

@Import(org.camunda.optimize.Main.class)
@Configuration
@IgnoreDuringScan
public class SpringDefaultITConfig {
  @Bean
  @Primary
  public static ConfigurationService configurationService() {
    return createConfigurationFromLocations("service-config.yaml", "it/it-config.yaml");
  }
}
