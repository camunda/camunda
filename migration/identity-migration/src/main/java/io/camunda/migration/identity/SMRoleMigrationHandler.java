/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity;

import static io.camunda.migration.identity.midentity.ManagementIdentityTransformer.toMigrationStatusUpdateRequest;
import static io.camunda.migration.identity.transformer.AuthorizationTransformer.transform;

import io.camunda.migration.identity.console.ConsoleClient;
import io.camunda.migration.identity.dto.MigrationStatusUpdateRequest;
import io.camunda.migration.identity.dto.Role;
import io.camunda.migration.identity.midentity.ManagementIdentityClient;
import io.camunda.search.entities.RoleEntity;
import io.camunda.security.auth.Authentication;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.RoleServices;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnMissingBean(ConsoleClient.class)
public class SMRoleMigrationHandler extends RoleMigrationHandler {
  private static final Logger LOG = LoggerFactory.getLogger(SMRoleMigrationHandler.class);
  private final RoleServices roleServices;
  private final AuthorizationServices authorizationServices;
  private final ManagementIdentityClient managementIdentityClient;

  public SMRoleMigrationHandler(
      final RoleServices roleServices,
      final AuthorizationServices authorizationServices,
      final Authentication servicesAuthentication,
      final ManagementIdentityClient managementIdentityClient) {
    this.authorizationServices = authorizationServices.withAuthentication(servicesAuthentication);
    this.roleServices = roleServices.withAuthentication(servicesAuthentication);
    this.managementIdentityClient = managementIdentityClient;
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
      LOG.error("create or finding role with name {} failed", role.name(), e);
      return toMigrationStatusUpdateRequest(role, e);
    }

    try {
      transform(roleKey, role.permissions()).stream()
          .map(authorizationServices::patchAuthorization)
          .forEach(CompletableFuture::join);
    } catch (final Exception e) {
      LOG.error("patch authorization for role failed", e);
      if (!isConflictError(e)) {
        return toMigrationStatusUpdateRequest(role, e);
      }
    }
    return toMigrationStatusUpdateRequest(role, null);
  }

  @Override
  protected List<Role> fetchBatch() {
    return managementIdentityClient.fetchRoles(SIZE);
  }

  @Override
  protected void process(final List<Role> batch) {
    managementIdentityClient.updateMigrationStatus(batch.stream().map(this::createRole).toList());
  }
}
