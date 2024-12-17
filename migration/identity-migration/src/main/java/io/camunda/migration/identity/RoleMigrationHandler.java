/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity;

import io.camunda.migration.identity.dto.MigrationStatusUpdateRequest;
import io.camunda.migration.identity.dto.Role;
import io.camunda.migration.identity.midentity.ManagementIdentityClient;
import io.camunda.migration.identity.midentity.ManagementIdentityTransformer;
import io.camunda.migration.identity.service.AuthorizationService;
import io.camunda.migration.identity.service.RoleService;
import io.camunda.search.entities.RoleEntity;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RoleMigrationHandler implements MigrationHandler {
  private final RoleService roleService;
  private final AuthorizationService authorizationService;
  private final ManagementIdentityClient managementIdentityClient;
  private final ManagementIdentityTransformer managementIdentityTransformer;

  public RoleMigrationHandler(
      final RoleService roleService,
      final AuthorizationService authorizationService,
      final ManagementIdentityClient managementIdentityClient,
      final ManagementIdentityTransformer managementIdentityTransformer) {
    this.roleService = roleService;
    this.authorizationService = authorizationService;
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
    final RoleEntity roleEntity;
    try {
      roleEntity = roleService.createOrFetch(role.name());
    } catch (final Exception e) {
      return managementIdentityTransformer.toMigrationStatusUpdateRequest(role, e);
    }

    try {
      authorizationService.patch(roleEntity.roleKey(), role.permissions());
    } catch (final Exception e) {
      if (!isConflictError(e)) {
        return managementIdentityTransformer.toMigrationStatusUpdateRequest(role, e);
      }
    }
    return managementIdentityTransformer.toMigrationStatusUpdateRequest(role, null);
  }
}
