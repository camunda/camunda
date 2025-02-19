/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize;

import static io.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder.createConfigurationFromLocations;

import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.condition.CCSMCondition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;

@Import(io.camunda.optimize.TestsEntry.class)
@Configuration
@Conditional(CCSMCondition.class)
public class SpringDefaultC8ITConfig {

  @Bean
  @Primary
  public static ConfigurationService configurationService() {
    return createConfigurationFromLocations("service-config.yaml", "it/it-config-ccsm.yaml");
  }
}
