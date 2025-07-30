/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.config.saas;

import io.camunda.migration.identity.client.ConsoleClient;
import io.camunda.migration.identity.client.ManagementIdentityClient;
import io.camunda.migration.identity.config.IdentityMigrationProperties;
import io.camunda.migration.identity.handler.saas.AuthorizationMigrationHandler;
import io.camunda.migration.identity.handler.saas.ClientMigrationHandler;
import io.camunda.migration.identity.handler.saas.GroupMigrationHandler;
import io.camunda.migration.identity.handler.saas.StaticConsoleRoleAuthorizationMigrationHandler;
import io.camunda.migration.identity.handler.saas.StaticConsoleRoleMigrationHandler;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.GroupServices;
import io.camunda.service.RoleServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnCloud
public class SaaSMigrationHandlerConfig {
  @Bean
  public GroupMigrationHandler groupMigrationHandler(
      final CamundaAuthentication authentication,
      final ConsoleClient consoleClient,
      final ManagementIdentityClient managementIdentityClient,
      final GroupServices groupServices,
      final IdentityMigrationProperties migrationProperties) {
    return new GroupMigrationHandler(
        authentication,
        consoleClient,
        managementIdentityClient,
        groupServices,
        migrationProperties);
  }

  @Bean
  public StaticConsoleRoleMigrationHandler roleMigrationHandler(
      final CamundaAuthentication authentication,
      final RoleServices roleServices,
      final ConsoleClient consoleClient,
      final IdentityMigrationProperties migrationProperties) {
    return new StaticConsoleRoleMigrationHandler(
        roleServices, authentication, consoleClient, migrationProperties);
  }

  @Bean
  public StaticConsoleRoleAuthorizationMigrationHandler
      staticConsoleRoleAuthorizationMigrationHandler(
          final AuthorizationServices authorizationService,
          final CamundaAuthentication authentication,
          final IdentityMigrationProperties migrationProperties) {
    return new StaticConsoleRoleAuthorizationMigrationHandler(
        authorizationService, authentication, migrationProperties);
  }

  @Bean
  public AuthorizationMigrationHandler authorizationMigrationHandler(
      final CamundaAuthentication authentication,
      final AuthorizationServices authorizationService,
      final ConsoleClient consoleClient,
      final ManagementIdentityClient managementIdentityClient,
      final IdentityMigrationProperties migrationProperties) {
    return new AuthorizationMigrationHandler(
        authentication,
        authorizationService,
        consoleClient,
        managementIdentityClient,
        migrationProperties);
  }

  @Bean
  public ClientMigrationHandler clientMigrationHandler(
      final ConsoleClient consoleClient,
      final AuthorizationServices authorizationServices,
      final CamundaAuthentication servicesAuthentication,
      final IdentityMigrationProperties migrationProperties) {
    return new ClientMigrationHandler(
        consoleClient, authorizationServices, servicesAuthentication, migrationProperties);
  }
}
