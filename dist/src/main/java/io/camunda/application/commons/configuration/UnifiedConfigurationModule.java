/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.configuration;

import io.camunda.application.commons.secrets.ConfigurationReferences;
import io.camunda.application.commons.secrets.ConfigurationSecretStores;
import io.camunda.application.commons.secrets.ConfigurationSecrets;
import io.camunda.configuration.Camunda;
import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Aggregator for the unified configuration module. Importing this single class registers {@code
 * UnifiedConfiguration}, {@code UnifiedConfigurationHelper}, and every {@code *Override}
 * configuration under {@code io.camunda.configuration}. Each override self-gates via
 * {@code @Profile} or other conditional annotations, so only the beans relevant to the active
 * profiles are instantiated.
 */
@Configuration(proxyBeanMethods = false)
@ComponentScan("io.camunda.configuration")
public class UnifiedConfigurationModule {

  @Bean
  public static ConfigurationSecrets configurationSecrets(final Environment environment) {
    return new ConfigurationSecrets(environment);
  }

  @Bean
  public PhysicalTenantResolver physicalTenantResolver(
      final ConfigurationSecrets configurationSecrets, final Camunda camunda) {
    return configurationSecrets.resolve(camunda);
  }

  @Bean
  public ConfigurationSecretStores configurationSecretStores(
      final ConfigurationSecrets configurationSecrets, final PhysicalTenantResolver resolver) {
    return configurationSecrets.stores();
  }

  @Bean
  public ConfigurationReferences configurationReferences(
      final ConfigurationSecrets configurationSecrets, final PhysicalTenantResolver resolver) {
    return configurationSecrets.references();
  }
}
