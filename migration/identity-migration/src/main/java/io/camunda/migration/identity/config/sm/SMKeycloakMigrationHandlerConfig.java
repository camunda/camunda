/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.config.sm;

import io.camunda.migration.identity.client.ManagementIdentityClient;
import io.camunda.migration.identity.handler.sm.GroupMigrationHandler;
import io.camunda.migration.identity.handler.sm.RoleMigrationHandler;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.GroupServices;
import io.camunda.service.RoleServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnKeycloak
public class SMKeycloakMigrationHandlerConfig {
  @Bean
  public RoleMigrationHandler roleMigrationHandler(
      final CamundaAuthentication authentication,
      final ManagementIdentityClient managementIdentityClient,
      final RoleServices roleServices,
      final AuthorizationServices authorizationServices) {
    return new RoleMigrationHandler(
        authentication, managementIdentityClient, roleServices, authorizationServices);
  }

  @Bean
  public GroupMigrationHandler groupMigrationHandler(
      final ManagementIdentityClient managementIdentityClient, final GroupServices groupServices) {
    return new GroupMigrationHandler(managementIdentityClient, groupServices);
  }
}
