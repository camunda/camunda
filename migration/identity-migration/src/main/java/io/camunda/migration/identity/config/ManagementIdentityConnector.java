/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.config;

import io.camunda.migration.identity.midentity.ManagementIdentityClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ManagementIdentityConnector {
  @Autowired private IdentityMigrationProperties identityMigrationProperties;

  @Bean // will be closed automatically
  public ManagementIdentityClient managementIdentityClient(final RestTemplateBuilder builder) {
    final var properties = identityMigrationProperties.getManagementIdentity();
    final var restTemplate =
        builder
            .rootUri(properties.getBaseUrl())
            .defaultHeader("Authorization", String.format("Bearer %s", properties.getM2mToken()))
            .defaultHeader("Content-Type", "application/json")
            .defaultHeader("Accept", "application/json")
            .build();
    return new ManagementIdentityClient(restTemplate);
  }
}
