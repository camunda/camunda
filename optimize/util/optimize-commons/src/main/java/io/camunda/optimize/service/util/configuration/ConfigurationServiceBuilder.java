/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

import java.util.List;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ConfigurationServiceBuilder {

  public static final List<String> DEFAULT_CONFIG_LOCATIONS =
      List.of("service-config.yaml", "environment-config.yaml");

  private String[] configLocations = DEFAULT_CONFIG_LOCATIONS.toArray(new String[] {});
  private ConfigurationValidator configurationValidator = new ConfigurationValidator();

  @Bean
  @Qualifier("configurationService")
  public static ConfigurationService createDefaultConfiguration() {
    return createConfiguration().build();
  }

  public static ConfigurationService createConfigurationFromLocations(
      final String... configLocations) {
    return createConfiguration().loadConfigurationFrom(configLocations).build();
  }

  public static ConfigurationService createConfigurationWithDefaultAndAdditionalLocations(
      final String... additionalLocations) {
    return createConfiguration().addConfigurationLocations(additionalLocations).build();
  }

  public static ConfigurationServiceBuilder createConfiguration() {
    return new ConfigurationServiceBuilder();
  }

  public ConfigurationServiceBuilder addConfigurationLocations(final String... configLocations) {
    this.configLocations = ArrayUtils.addAll(this.configLocations, configLocations);
    return this;
  }

  public ConfigurationServiceBuilder loadConfigurationFrom(final String... configLocations) {
    this.configLocations = configLocations;
    return this;
  }

  public ConfigurationServiceBuilder useValidator(
      final ConfigurationValidator configurationValidator) {
    this.configurationValidator = configurationValidator;
    return this;
  }

  public ConfigurationService build() {
    return new ConfigurationService(configLocations, configurationValidator);
  }
}
