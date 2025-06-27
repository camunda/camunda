/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.handler.sm;

import io.camunda.migration.api.MigrationException;
import io.camunda.migration.identity.client.ManagementIdentityClient;
import io.camunda.migration.identity.dto.Role;
import io.camunda.migration.identity.handler.MigrationHandler;
import io.camunda.security.auth.Authentication;
import io.camunda.service.RoleServices;
import io.camunda.service.RoleServices.CreateRoleRequest;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RoleMigrationHandler extends MigrationHandler<Role> {

  private final ManagementIdentityClient managementIdentityClient;
  private final RoleServices roleServices;

  private final AtomicInteger createdRoleCount = new AtomicInteger();
  private final AtomicInteger totalRoleCount = new AtomicInteger();

  public RoleMigrationHandler(
      final Authentication authentication,
      final ManagementIdentityClient managementIdentityClient,
      final RoleServices roleServices) {
    this.managementIdentityClient = managementIdentityClient;
    this.roleServices = roleServices.withAuthentication(authentication);
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
            final var roleId = normalizeRoleID(role.name());
            roleServices.createRole(new CreateRoleRequest(roleId, role.name(), role.description()));
            createdRoleCount.incrementAndGet();
            logger.info("Role '{}' with ID '{}' created successfully.", role.name(), roleId);
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
}
