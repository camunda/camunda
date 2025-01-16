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
import io.camunda.migration.identity.config.prop.ManagementIdentityProperties;
import io.camunda.migration.identity.midentity.ManagementIdentityClient;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ManagementIdentityConnector {

  public Identity identity(final ManagementIdentityProperties properties) {
    final var configuration =
        new IdentityConfiguration(
            properties.getBaseUrl(),
            properties.getIssuerBackendUrl(),
            properties.getIssuerBackendUrl(),
            properties.getClientId(),
            properties.getClientSecret(),
            properties.getAudience(),
            properties.getIssuerType());
    return new Identity(configuration);
  }

  @Bean
  public ManagementIdentityClient managementIdentityClient(
      final RestTemplateBuilder builder,
      final ManagementIdentityProperties properties,
      final ClusterProperties clusterProperties) {
    final var restTemplate =
        builder
            .rootUri(properties.getBaseUrl())
            .interceptors(new M2MTokenInterceptor(identity(properties), properties.getAudience()))
            .defaultHeader("Content-Type", "application/json")
            .defaultHeader("Accept", "application/json")
            .build();
    return new ManagementIdentityClient(restTemplate, clusterProperties.getOrganizationId());
  }
}
