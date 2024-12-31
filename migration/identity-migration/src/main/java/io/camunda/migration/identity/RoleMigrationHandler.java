/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity;

import static io.camunda.migration.identity.transformer.AuthorizationTransformer.transform;

import io.camunda.migration.identity.dto.MigrationStatusUpdateRequest;
import io.camunda.migration.identity.dto.Role;
import io.camunda.migration.identity.midentity.ManagementIdentityClient;
import io.camunda.migration.identity.midentity.ManagementIdentityTransformer;
import io.camunda.search.entities.RoleEntity;
import io.camunda.security.auth.Authentication;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.RoleServices;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Component;

@Component
public class RoleMigrationHandler implements MigrationHandler {
  private final RoleServices roleServices;
  private final AuthorizationServices authorizationServices;
  private final ManagementIdentityClient managementIdentityClient;
  private final ManagementIdentityTransformer managementIdentityTransformer;

  public RoleMigrationHandler(
      final RoleServices roleServices,
      final AuthorizationServices authorizationServices,
      final Authentication servicesAuthentication,
      final ManagementIdentityClient managementIdentityClient,
      final ManagementIdentityTransformer managementIdentityTransformer) {
    this.authorizationServices = authorizationServices.withAuthentication(servicesAuthentication);
    this.roleServices = roleServices.withAuthentication(servicesAuthentication);
    this.managementIdentityClient = managementIdentityClient;
    this.managementIdentityTransformer = managementIdentityTransformer;
  }

  @Override
  public void migrate() {
    List<Role> roles;
    do {
      roles = managementIdentityClient.fetchRoles(SIZE);
      managementIdentityClient.updateMigrationStatus(roles.stream().map(this::createRole).toList());
    } while (!roles.isEmpty());
  }

  private MigrationStatusUpdateRequest createRole(final Role role) {
    final long roleKey;
    try {
      roleKey =
          roleServices
              .findRole(role.name())
              .map(RoleEntity::roleKey)
              .orElseGet(() -> roleServices.createRole(role.name()).join().getRoleKey());
    } catch (final Exception e) {
      return managementIdentityTransformer.toMigrationStatusUpdateRequest(role, e);
    }

    try {
      transform(roleKey, role.permissions()).stream()
          .map(authorizationServices::patchAuthorization)
          .forEach(CompletableFuture::join);
    } catch (final Exception e) {
      if (!isConflictError(e)) {
        return managementIdentityTransformer.toMigrationStatusUpdateRequest(role, e);
      }
    }
    return managementIdentityTransformer.toMigrationStatusUpdateRequest(role, null);
  }
}
