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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the migration handlers used in the SaaS / cloud profile. Each handler can be disabled via
 * its corresponding {@code camunda.migration.identity.handler.cloud.<name>.enabled=false} property.
 * {@code console-role-authorization} consumes the static roles created by {@code console-role}, and
 * {@code authorization} consumes groups created by {@code group}; disabling a predecessor while
 * keeping a consumer enabled raises a {@link io.camunda.migration.api.MigrationException} at
 * runtime.
 */
@Configuration
@ConditionalOnCloud
public class SaaSMigrationHandlerConfig {

  public static final String GROUP_ENABLED =
      "camunda.migration.identity.handler.cloud.group.enabled";
  public static final String CONSOLE_ROLE_ENABLED =
      "camunda.migration.identity.handler.cloud.console-role.enabled";
  public static final String CONSOLE_ROLE_AUTHORIZATION_ENABLED =
      "camunda.migration.identity.handler.cloud.console-role-authorization.enabled";
  public static final String AUTHORIZATION_ENABLED =
      "camunda.migration.identity.handler.cloud.authorization.enabled";
  public static final String CLIENT_ENABLED =
      "camunda.migration.identity.handler.cloud.client.enabled";

  @Bean
  @ConditionalOnProperty(name = GROUP_ENABLED, matchIfMissing = true)
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
  @ConditionalOnProperty(name = CONSOLE_ROLE_ENABLED, matchIfMissing = true)
  public StaticConsoleRoleMigrationHandler roleMigrationHandler(
      final CamundaAuthentication authentication,
      final RoleServices roleServices,
      final ConsoleClient consoleClient,
      final IdentityMigrationProperties migrationProperties) {
    return new StaticConsoleRoleMigrationHandler(
        roleServices, authentication, consoleClient, migrationProperties);
  }

  @Bean
  @ConditionalOnProperty(name = CONSOLE_ROLE_AUTHORIZATION_ENABLED, matchIfMissing = true)
  public StaticConsoleRoleAuthorizationMigrationHandler
      staticConsoleRoleAuthorizationMigrationHandler(
          final AuthorizationServices authorizationService,
          final CamundaAuthentication authentication,
          final IdentityMigrationProperties migrationProperties) {
    return new StaticConsoleRoleAuthorizationMigrationHandler(
        authorizationService, authentication, migrationProperties);
  }

  @Bean
  @ConditionalOnProperty(name = AUTHORIZATION_ENABLED, matchIfMissing = true)
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
  @ConditionalOnProperty(name = CLIENT_ENABLED, matchIfMissing = true)
  public ClientMigrationHandler clientMigrationHandler(
      final ConsoleClient consoleClient,
      final AuthorizationServices authorizationServices,
      final CamundaAuthentication servicesAuthentication,
      final IdentityMigrationProperties migrationProperties) {
    return new ClientMigrationHandler(
        consoleClient, authorizationServices, servicesAuthentication, migrationProperties);
  }
}
