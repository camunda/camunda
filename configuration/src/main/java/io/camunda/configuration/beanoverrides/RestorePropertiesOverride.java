/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.configuration.beanoverrides;

import io.camunda.configuration.UnifiedConfiguration;
import io.camunda.configuration.beans.RestoreProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile(value = {"broker", "restore"})
@DependsOn("unifiedConfigurationHelper")
public class RestorePropertiesOverride {

  private final UnifiedConfiguration unifiedConfiguration;

  public RestorePropertiesOverride(final UnifiedConfiguration unifiedConfiguration) {
    this.unifiedConfiguration = unifiedConfiguration;
  }

  @Bean
  @Primary
  public RestoreProperties restoreProperties() {
    final var restoreProps = unifiedConfiguration.getCamunda().getSystem().getRestore();
    return new RestoreProperties(
        restoreProps.isValidateConfig(), restoreProps.getIgnoreFilesInTarget());
  }
}
