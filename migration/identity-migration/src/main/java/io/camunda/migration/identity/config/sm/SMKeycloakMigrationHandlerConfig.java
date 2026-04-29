/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.config.sm;

import io.camunda.migration.identity.client.ManagementIdentityClient;
import io.camunda.migration.identity.config.IdentityMigrationProperties;
import io.camunda.migration.identity.handler.sm.AuthorizationMigrationHandler;
import io.camunda.migration.identity.handler.sm.ClientMigrationHandler;
import io.camunda.migration.identity.handler.sm.GroupMigrationHandler;
import io.camunda.migration.identity.handler.sm.RoleMigrationHandler;
import io.camunda.migration.identity.handler.sm.TenantMigrationHandler;
import io.camunda.migration.identity.handler.sm.UserRoleMigrationHandler;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
@ConditionalOnKeycloak
public class SMKeycloakMigrationHandlerConfig {
  @Bean
  @Order(1)
  @ConditionalOnProperty(
      name = "camunda.migration.identity.handler.keycloak.role.enabled",
      matchIfMissing = true)
  public RoleMigrationHandler roleMigrationHandler(
      final CamundaAuthentication authentication,
      final ManagementIdentityClient managementIdentityClient,
      final RoleServices roleServices,
      final AuthorizationServices authorizationServices,
      final IdentityMigrationProperties migrationProperties) {
    return new RoleMigrationHandler(
        authentication,
        managementIdentityClient,
        roleServices,
        authorizationServices,
        migrationProperties);
  }

  @Bean
  @Order(2)
  @ConditionalOnProperty(
      name = "camunda.migration.identity.handler.keycloak.group.enabled",
      matchIfMissing = true)
  public GroupMigrationHandler groupMigrationHandler(
      final ManagementIdentityClient managementIdentityClient,
      final AuthorizationServices authorizationServices,
      final RoleServices roleServices,
      final CamundaAuthentication authentication,
      final IdentityMigrationProperties migrationProperties) {
    return new GroupMigrationHandler(
        managementIdentityClient,
        authorizationServices,
        roleServices,
        authentication,
        migrationProperties);
  }

  @Bean
  @Order(3)
  @ConditionalOnProperty(
      name = "camunda.migration.identity.handler.keycloak.user-role.enabled",
      matchIfMissing = true)
  public UserRoleMigrationHandler userRoleMigrationHandler(
      final CamundaAuthentication authentication,
      final ManagementIdentityClient managementIdentityClient,
      final RoleServices roleServices,
      final IdentityMigrationProperties migrationProperties) {
    return new UserRoleMigrationHandler(
        authentication, managementIdentityClient, roleServices, migrationProperties);
  }

  @Bean
  @Order(4)
  @ConditionalOnProperty(
      name = "camunda.migration.identity.handler.keycloak.client.enabled",
      matchIfMissing = true)
  public ClientMigrationHandler clientMigrationHandler(
      final CamundaAuthentication authentication,
      final ManagementIdentityClient managementIdentityClient,
      final AuthorizationServices authorizationService,
      final IdentityMigrationProperties migrationProperties) {
    return new ClientMigrationHandler(
        authentication, managementIdentityClient, authorizationService, migrationProperties);
  }

  @Bean
  @Order(5)
  @ConditionalOnProperty(
      name = "camunda.migration.identity.handler.keycloak.authorization.enabled",
      matchIfMissing = true)
  public AuthorizationMigrationHandler authorizationMigrationHandler(
      final CamundaAuthentication authentication,
      final AuthorizationServices authorizationService,
      final ManagementIdentityClient managementIdentityClient,
      final IdentityMigrationProperties migrationProperties) {
    return new AuthorizationMigrationHandler(
        authentication, authorizationService, managementIdentityClient, migrationProperties);
  }

  @Bean
  @Order(6)
  @ConditionalOnProperty(
      name = "camunda.migration.identity.handler.keycloak.tenant.enabled",
      matchIfMissing = true)
  public TenantMigrationHandler tenantMigrationHandler(
      final ManagementIdentityClient managementIdentityClient,
      final TenantServices tenantService,
      final CamundaAuthentication camundaAuthentication,
      final IdentityMigrationProperties migrationProperties) {
    return new TenantMigrationHandler(
        managementIdentityClient, tenantService, camundaAuthentication, migrationProperties);
  }
}
