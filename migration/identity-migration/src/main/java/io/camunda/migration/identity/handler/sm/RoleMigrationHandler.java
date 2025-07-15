/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.handler.sm;

import static io.camunda.migration.identity.MigrationUtil.extractCombinedPermissions;
import static io.camunda.migration.identity.MigrationUtil.normalizeID;
import static io.camunda.migration.identity.config.sm.StaticEntities.getAuthorizationsByAudience;

import io.camunda.migration.api.MigrationException;
import io.camunda.migration.identity.client.ManagementIdentityClient;
import io.camunda.migration.identity.dto.Role;
import io.camunda.migration.identity.handler.MigrationHandler;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.RoleServices;
import io.camunda.service.RoleServices.CreateRoleRequest;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RoleMigrationHandler extends MigrationHandler<Role> {

  private final ManagementIdentityClient managementIdentityClient;
  private final RoleServices roleServices;
  private final AuthorizationServices authorizationServices;

  private final AtomicInteger createdRoleCount = new AtomicInteger();
  private final AtomicInteger totalRoleCount = new AtomicInteger();

  public RoleMigrationHandler(
      final CamundaAuthentication authentication,
      final ManagementIdentityClient managementIdentityClient,
      final RoleServices roleServices,
      final AuthorizationServices authorizationServices) {
    this.managementIdentityClient = managementIdentityClient;
    this.roleServices = roleServices.withAuthentication(authentication);
    this.authorizationServices = authorizationServices.withAuthentication(authentication);
  }

  @Override
  protected List<Role> fetchBatch(final int page) {
    // roles are not paginated in the Management Identity
    return List.of();
  }

  @Override
  protected void process(final List<Role> batch) {
    final var roles = managementIdentityClient.fetchRoles();
    totalRoleCount.set(roles.size());

    roles.forEach(
        role -> {
          final var roleName = role.name();
          final var roleId = normalizeID(roleName);
          try {
            roleServices
                .createRole(new CreateRoleRequest(roleId, roleName, role.description()))
                .join();
            createdRoleCount.incrementAndGet();
            logger.debug("Role '{}' with ID '{}' created successfully.", roleName, roleId);
          } catch (final Exception e) {
            if (!isConflictError(e)) {
              throw new MigrationException("Failed to migrate role with name: " + role.name(), e);
            }
            logger.debug("Role with name '{}' already exists, skipping creation.", role.name());
          }
          createAuthorizationsForRole(roleId, roleName);
        });
  }

  @Override
  protected void logSummary() {
    logger.info(
        "Role Migration completed: Created {} roles out of {} total roles.",
        createdRoleCount.get(),
        totalRoleCount.get());
  }

  private void createAuthorizationsForRole(final String roleId, final String roleName) {
    final List<String> permissions = getFormattedPermissions(roleName);

    if (permissions.isEmpty()) {
      logger.debug(
          "No permissions found for role '{}', skipping authorization creation.", roleName);
      return;
    }

    final var authorizations =
        permissions.stream()
            .map(
                permission ->
                    getAuthorizationsByAudience(permission, roleId, AuthorizationOwnerType.ROLE))
            .flatMap(List::stream)
            .toList();

    final var combinedPermissions = extractCombinedPermissions(authorizations);

    for (final var request : combinedPermissions) {
      try {
        authorizationServices.createAuthorization(request).join();
        logger.debug(
            "Authorization created for role '{}' with permissions '{}'.",
            roleName,
            request.permissionTypes());
      } catch (final Exception e) {
        if (!isConflictError(e)) {
          throw new MigrationException(
              String.format(
                  "Failed to create authorization for role '%s' with permissions '%s'",
                  roleName, request.permissionTypes()),
              e);
        }
        logger.debug(
            "Authorization already exists for role '{}' with permissions '{}', skipping.",
            roleName,
            request.permissionTypes());
      }
    }
  }

  private List<String> getFormattedPermissions(final String roleName) {
    return managementIdentityClient.fetchPermissions(roleName).stream()
        .map(
            permission ->
                String.format(
                    "%s:%s", permission.resourceServerAudience(), permission.definition()))
        .toList();
  }
}
