/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize;

import io.camunda.optimize.service.util.configuration.ConfigurationService;
import io.camunda.optimize.service.util.configuration.ConfigurationServiceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;

@Import(io.camunda.optimize.Main.class)
@Configuration
@Profile("variable-import-disabled")
public class SpringC8VariableImportDisabledITConfig {

  @Bean
  @Primary
  public static ConfigurationService configurationService(final Environment environment) {
    return ConfigurationServiceBuilder.createConfigurationService(
        environment, "service-config.yaml", "it/it-config-ccsm-variables-disabled.yaml");
  }
}
