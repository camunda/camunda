/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.handler.sm;

import static io.camunda.migration.identity.MigrationUtil.extractCombinedPermissions;
import static io.camunda.migration.identity.config.sm.StaticEntities.getAuthorizationsByAudience;

import io.camunda.migration.api.MigrationException;
import io.camunda.migration.identity.client.ManagementIdentityClient;
import io.camunda.migration.identity.dto.Role;
import io.camunda.migration.identity.handler.MigrationHandler;
import io.camunda.security.auth.Authentication;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.RoleServices;
import io.camunda.service.RoleServices.CreateRoleRequest;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RoleMigrationHandler extends MigrationHandler<Role> {

  private final ManagementIdentityClient managementIdentityClient;
  private final RoleServices roleServices;
  private final AuthorizationServices authorizationServices;

  private final AtomicInteger createdRoleCount = new AtomicInteger();
  private final AtomicInteger totalRoleCount = new AtomicInteger();

  public RoleMigrationHandler(
      final Authentication authentication,
      final ManagementIdentityClient managementIdentityClient,
      final RoleServices roleServices,
      final AuthorizationServices authorizationServices) {
    this.managementIdentityClient = managementIdentityClient;
    this.roleServices = roleServices.withAuthentication(authentication);
    this.authorizationServices = authorizationServices;
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
          try {
            final var roleName = role.name();
            final var roleId = normalizeRoleID(roleName);
            roleServices.createRole(new CreateRoleRequest(roleId, roleName, role.description()));
            createdRoleCount.incrementAndGet();
            logger.debug("Role '{}' with ID '{}' created successfully.", roleName, roleId);
            createAuthorizationsForRole(roleId, roleName);
          } catch (final Exception e) {
            if (!isConflictError(e)) {
              throw new MigrationException("Failed to migrate role with name: " + role.name(), e);
            }
            logger.debug("Role with name '{}' already exists, skipping creation.", role.name());
          }
        });
  }

  @Override
  protected void logSummary() {
    logger.info(
        "Role Migration completed: Created {} roles out of {} total roles.",
        createdRoleCount.get(),
        totalRoleCount.get());
  }

  // Normalizes the role ID to ensure it meets the requirements for a valid role ID.
  // For SM the role ID is derived from the role name, because in the old identity
  // management system the role ID was generated internally.
  private String normalizeRoleID(final String roleName) {
    String normalizedId =
        roleName.toLowerCase().replaceAll("[^a-z0-9_@.-]", "_"); // Replace disallowed characters

    if (normalizedId.length() > 256) {
      normalizedId = normalizedId.substring(0, 256);
    }
    return normalizedId;
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
            .map(permission -> getAuthorizationsByAudience(permission, roleId))
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
