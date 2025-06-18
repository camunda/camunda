/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.config;

import io.camunda.migration.identity.GroupMigrationHandler;
import io.camunda.migration.identity.StaticConsoleAuthorizationMigrationHandler;
import io.camunda.migration.identity.StaticConsoleRoleMigrationHandler;
import io.camunda.migration.identity.console.ConsoleClient;
import io.camunda.migration.identity.midentity.ManagementIdentityClient;
import io.camunda.security.auth.Authentication;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.GroupServices;
import io.camunda.service.RoleServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnCloud
public class MigrationHandlerConfig {
  @Bean
  public GroupMigrationHandler groupMigrationHandler(
      final Authentication authentication,
      final ConsoleClient consoleClient,
      final ManagementIdentityClient managementIdentityClient,
      final GroupServices groupServices) {
    return new GroupMigrationHandler(
        authentication, consoleClient, managementIdentityClient, groupServices);
  }

  @Bean
  public StaticConsoleRoleMigrationHandler roleMigrationHandler(
      final Authentication authentication,
      final RoleServices roleServices,
      final ConsoleClient consoleClient) {
    return new StaticConsoleRoleMigrationHandler(roleServices, authentication, consoleClient);
  }

  @Bean
  public StaticConsoleAuthorizationMigrationHandler authorizationMigrationHandler(
      final AuthorizationServices authorizationService, final Authentication authentication) {
    return new StaticConsoleAuthorizationMigrationHandler(authorizationService, authentication);
  }
}
