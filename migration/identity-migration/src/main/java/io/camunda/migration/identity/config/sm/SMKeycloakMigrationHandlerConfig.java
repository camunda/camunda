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
import io.camunda.migration.identity.handler.sm.GroupAuthorizationMigrationHandler;
import io.camunda.migration.identity.handler.sm.RoleMigrationHandler;
import io.camunda.migration.identity.handler.sm.TenantMigrationHandler;
import io.camunda.migration.identity.handler.sm.UserRoleMigrationHandler;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.RoleServices;
import io.camunda.service.TenantServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

@Configuration
@ConditionalOnKeycloak
public class SMKeycloakMigrationHandlerConfig {
  @Bean
  @Order(1)
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
  public GroupAuthorizationMigrationHandler groupMigrationHandler(
      final ManagementIdentityClient managementIdentityClient,
      final AuthorizationServices authorizationServices,
      final CamundaAuthentication authentication,
      final IdentityMigrationProperties migrationProperties) {
    return new GroupAuthorizationMigrationHandler(
        managementIdentityClient, authorizationServices, authentication, migrationProperties);
  }

  @Bean
  @Order(3)
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
  public TenantMigrationHandler tenantMigrationHandler(
      final ManagementIdentityClient managementIdentityClient,
      final TenantServices tenantService,
      final CamundaAuthentication camundaAuthentication,
      final IdentityMigrationProperties migrationProperties) {
    return new TenantMigrationHandler(
        managementIdentityClient, tenantService, camundaAuthentication, migrationProperties);
  }
}
