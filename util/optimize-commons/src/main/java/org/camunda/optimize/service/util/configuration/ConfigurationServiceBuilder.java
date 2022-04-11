/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util.configuration;

import org.apache.commons.lang3.ArrayUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class ConfigurationServiceBuilder {

  public static final List<String> DEFAULT_CONFIG_LOCATIONS = List.of(
    "service-config.yaml", "environment-config.yaml"
  );

  private String[] configLocations = DEFAULT_CONFIG_LOCATIONS.toArray(new String[]{});
  private ConfigurationValidator configurationValidator = new ConfigurationValidator();

  @Bean
  @Qualifier("configurationService")
  public static ConfigurationService createDefaultConfiguration() {
    return createConfiguration().build();
  }

  public static ConfigurationService createConfigurationFromLocations(String... configLocations) {
    return createConfiguration().loadConfigurationFrom(configLocations).build();
  }

  public static ConfigurationService createConfigurationWithDefaultAndAdditionalLocations(String... additionalLocations) {
    return createConfiguration().addConfigurationLocations(additionalLocations).build();
  }

  public static ConfigurationServiceBuilder createConfiguration() {
    return new ConfigurationServiceBuilder();
  }

  public ConfigurationServiceBuilder addConfigurationLocations(String... configLocations) {
    this.configLocations = ArrayUtils.addAll(this.configLocations, configLocations);
    return this;
  }

  public ConfigurationServiceBuilder loadConfigurationFrom(String... configLocations) {
    this.configLocations = configLocations;
    return this;
  }

  public ConfigurationServiceBuilder useValidator(ConfigurationValidator configurationValidator) {
    this.configurationValidator = configurationValidator;
    return this;
  }

  public ConfigurationService build() {
    return new ConfigurationService(configLocations, configurationValidator);
  }
}
