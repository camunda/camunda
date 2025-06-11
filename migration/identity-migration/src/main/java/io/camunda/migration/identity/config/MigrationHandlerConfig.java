/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.config;

import io.camunda.migration.identity.GroupMigrationHandler;
import io.camunda.migration.identity.midentity.ManagementIdentityClient;
import io.camunda.security.auth.Authentication;
import io.camunda.service.GroupServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnCloud
public class MigrationHandlerConfig {
  @Bean
  public GroupMigrationHandler groupMigrationHandler(
      final Authentication authentication,
      final ManagementIdentityClient managementIdentityClient,
      final GroupServices groupServices) {
    return new GroupMigrationHandler(authentication, managementIdentityClient, groupServices);
  }
}
