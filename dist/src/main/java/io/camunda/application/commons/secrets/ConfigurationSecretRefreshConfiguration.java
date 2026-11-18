/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.secrets;

import io.camunda.configuration.physicaltenants.PhysicalTenantResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ConfigurationSecretRefreshConfiguration {

  @Bean
  public RefreshableClients refreshableClients() {
    return new RefreshableClients();
  }

  @Bean
  public ConfigurationSecretRefresher configurationSecretRefresher(
      final PhysicalTenantResolver resolver,
      final ConfigurationSecretStores stores,
      final ConfigurationReferences references,
      final RefreshableClients clients) {
    return new ConfigurationSecretRefresher(resolver, stores, references, clients);
  }
}
