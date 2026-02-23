/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util.configuration;

import io.camunda.optimize.util.VisibleForTesting;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

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
    final ConfigurationServiceBuilder builder = createConfiguration();
    ConfigEnvironment.resolveConfigLocations(environment)
        .forEach(builder::addConfigurationLocations);
    return builder.build();
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
    final ConfigurationServiceBuilder builder =
        createConfiguration().loadConfigurationFrom(configLocations);
    ConfigEnvironment.resolveConfigLocations(environment)
        .forEach(builder::addConfigurationLocations);
    return builder.build();
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

  @VisibleForTesting
  static final class ConfigEnvironment {

    private static final Logger LOG = LoggerFactory.getLogger(ConfigEnvironment.class);

    /**
     * Resolves config locations from the given Spring Environment based on the properties
     * spring.config.import and spring.config.location.
     *
     * <p>For each location specified in spring.config.import, if spring.config.location is not set,
     * the location from the import will be added as is. If spring.config.location is set, then for
     * each location specified in spring.config.location, a location from the import relative to it
     * will be added. This allows to specify additional config files that are located relative to
     * the locations specified in spring.config.location.
     *
     * @param environment the Spring Environment to read properties from
     * @return a list of resolved config locations based on the given Spring Environment
     */
    static List<String> resolveConfigLocations(final Environment environment) {
      final List<String> locations = new ArrayList<>();

      final String springConfigImport = environment.getProperty("spring.config.import");
      final String springConfigLocation = environment.getProperty("spring.config.location");

      if (springConfigImport != null) {
        LOG.debug(
            "Resolving config locations from spring.config.import and spring.config.location");
        LOG.debug("spring.config.import is: {}", springConfigImport);
        for (String file : StringUtils.commaDelimitedListToStringArray(springConfigImport)) {
          LOG.debug("Considering config import file: {}", file);
          if (file.startsWith("optional:")) {
            file = file.substring("optional:".length());
          }
          if (file.startsWith("file:")) {
            file = file.substring("file:".length());
          }
          if (springConfigLocation == null || new java.io.File(file).isAbsolute()) {
            LOG.debug("Resolving config import file: {}", file);
            locations.add(file);
          } else {
            // if spring.config.location is set, then for each location, we need to add a file from
            // the import relative to it
            for (final String path :
                StringUtils.commaDelimitedListToStringArray(springConfigLocation)) {
              // remove trailing slashes to avoid double slashes when
              final var normalizedPath = path.replaceAll("/+$", "");
              final String resolvedFile = normalizedPath + "/" + file;
              LOG.debug("Resolving config import file: {}", resolvedFile);
              locations.add(resolvedFile);
            }
          }
        }
      }
      return locations;
    }
  }
}
