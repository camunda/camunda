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
import io.camunda.security.auth.Authentication;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.RoleServices;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RoleMigrationHandler extends MigrationHandler<Role> {
  private static final Logger LOG = LoggerFactory.getLogger(RoleMigrationHandler.class);
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
  protected List<Role> fetchBatch() {
    return managementIdentityClient.fetchRoles(SIZE);
  }

  @Override
  protected void process(final List<Role> batch) {
    managementIdentityClient.updateMigrationStatus(batch.stream().map(this::createRole).toList());
  }

  private MigrationStatusUpdateRequest createRole(final Role role) {
    final long roleKey;
    try {
      // TODO revisit with https://github.com/camunda/camunda/issues/26973
      //      roleKey =
      //          roleServices
      //              .findRole(role.name())
      //              .map(RoleEntity::roleKey)
      //              .orElseGet(() -> roleServices.createRole(role.name()).join().getRoleKey());
    } catch (final Exception e) {
      LOG.error("create or finding role with name {} failed", role.name(), e);
      return managementIdentityTransformer.toMigrationStatusUpdateRequest(role, e);
    }

    try {
      // TODO: this part needs to be revisited
      //      transform(roleKey, role.permissions()).stream()
      //          .map(authorizationServices::patchAuthorization)
      //          .forEach(CompletableFuture::join);
    } catch (final Exception e) {
      LOG.error("patch authorization for role failed", e);
      if (!isConflictError(e)) {
        return managementIdentityTransformer.toMigrationStatusUpdateRequest(role, e);
      }
    }
    return managementIdentityTransformer.toMigrationStatusUpdateRequest(role, null);
  }
}
