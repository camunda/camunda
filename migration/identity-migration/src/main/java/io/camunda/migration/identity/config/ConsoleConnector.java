/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.config;

import io.camunda.identity.sdk.Identity;
import io.camunda.identity.sdk.IdentityConfiguration;
import io.camunda.migration.identity.config.prop.ClusterProperties;
import io.camunda.migration.identity.config.prop.ConsoleProperties;
import io.camunda.migration.identity.console.ConsoleClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(value = "camunda.migration.identity.console.baseUrl", matchIfMissing = false)
public class ConsoleConnector {

  public Identity consoleIdentity(final ConsoleProperties properties) {
    final var configuration =
        new IdentityConfiguration(
            "",
            properties.getIssuerBackendUrl(),
            properties.getIssuerBackendUrl(),
            properties.getClientId(),
            properties.getClientSecret(),
            properties.getAudience(),
            "AUTH0");
    return new Identity(configuration);
  }

  @Bean
  public ConsoleClient consoleClient(
      final RestTemplateBuilder builder,
      final ConsoleProperties properties,
      final ClusterProperties clusterProperties) {
    final var restTemplate =
        builder
            .rootUri(properties.getBaseUrl())
            .interceptors(
                new M2MTokenInterceptor(consoleIdentity(properties), properties.getAudience()))
            .defaultHeader("Content-Type", "application/json")
            .defaultHeader("Accept", "application/json")
            .build();
    return new ConsoleClient(
        restTemplate,
        clusterProperties.getOrganizationId(),
        clusterProperties.getClusterId(),
        clusterProperties.getInternalClientId());
  }
}
