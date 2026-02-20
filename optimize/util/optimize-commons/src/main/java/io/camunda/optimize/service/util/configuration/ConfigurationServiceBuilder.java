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
import org.springframework.core.env.Environment;

@Configuration
public class ConfigurationServiceBuilder {

  public static final List<String> DEFAULT_CONFIG_LOCATIONS =
      List.of("service-config.yaml", "environment-config.yaml");

  private String[] configLocations = DEFAULT_CONFIG_LOCATIONS.toArray(new String[] {});
  private ConfigurationValidator configurationValidator = new ConfigurationValidator();

  /**
   * Creates a ConfigurationService bean that is configured based on the default config locations
   * and any additional config locations specified via spring.config.import and
   * spring.config.location environment properties.
   *
   * @param environment the Spring Environment to read properties from
   * @return the created ConfigurationService bean
   */
  @Bean
  @Qualifier("configurationService")
  public static ConfigurationService createConfigurationService(final Environment environment) {
    return createConfiguration().build();
  }

  /**
   * Creates a ConfigurationService based on the default config, the given additional config
   * locations, and any additional config locations specified via spring.config.import and
   * spring.config.location environment properties. The given config locations will be loaded on top
   * of the default config, and then any additional config locations from the environment will be
   * added on top of that.
   *
   * @param environment the Spring Environment to read properties from
   * @param configLocations config locations to load configuration from
   * @return the created ConfigurationService
   */
  public static ConfigurationService createConfigurationService(
      final Environment environment, final String... configLocations) {
    return createConfiguration().loadConfigurationFrom(configLocations).build();
  }

  /**
   * Creates a ConfigurationService based on the default config locations. This does not take into
   * account any additional config locations specified via spring.config.import and
   * spring.config.location environment properties, so it should only be used in contexts where
   * those properties are not expected to be set (e.g. in tests).
   *
   * @return the created ConfigurationService
   */
  public static ConfigurationService createDefaultConfiguration() {
    return createConfiguration().build();
  }

  /**
   * Creates a ConfigurationService based on the default config and the given additional config
   * locations. The given config locations will be loaded on top of the default config.
   *
   * @param additionalLocations config locations to load configuration from
   * @return the created ConfigurationService
   */
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
