/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.handler.saas;

import static io.camunda.migration.identity.config.saas.StaticEntities.ROLES;

import io.camunda.migration.api.MigrationException;
import io.camunda.migration.identity.client.ConsoleClient;
import io.camunda.migration.identity.config.IdentityMigrationProperties;
import io.camunda.migration.identity.dto.NoopDTO;
import io.camunda.migration.identity.handler.MigrationHandler;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.RoleServices;
import io.camunda.service.RoleServices.RoleMemberRequest;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class StaticConsoleRoleMigrationHandler extends MigrationHandler<NoopDTO> {
  private final RoleServices roleServices;
  private final ConsoleClient consoleClient;

  private final AtomicInteger createdRoleCount = new AtomicInteger();
  private final AtomicInteger assignedRoleCount = new AtomicInteger();
  private final AtomicInteger totalRoleAssignmentAttempts = new AtomicInteger();

  public StaticConsoleRoleMigrationHandler(
      final RoleServices roleServices,
      final CamundaAuthentication servicesAuthentication,
      final ConsoleClient consoleClient,
      final IdentityMigrationProperties migrationProperties) {
    super(migrationProperties.getBackpressureDelay());
    this.roleServices = roleServices.withAuthentication(servicesAuthentication);
    this.consoleClient = consoleClient;
  }

  @Override
  protected List<NoopDTO> fetchBatch(final int page) {
    // Roles are created statically
    return List.of();
  }

  @Override
  protected void process(final List<NoopDTO> batch) {
    createRoles();
    assignRolesToUsers();
  }

  @Override
  protected void logSummary() {
    logger.info(
        "Role migration complete: Created {} roles. Assigned {} out of {} user members to those, the remaining were already members.",
        createdRoleCount.get(),
        assignedRoleCount.get(),
        totalRoleAssignmentAttempts.get());
  }

  private void createRoles() {
    ROLES.forEach(
        role -> {
          try {
            retryOnBackpressure(
                () -> roleServices.createRole(role).join(),
                "migrating role with ID: " + role.roleId());
            createdRoleCount.incrementAndGet();
            logger.debug("Migrated role: {}", role);
          } catch (final Exception e) {
            if (!isConflictError(e)) {
              throw new MigrationException("Failed to migrate role with ID: " + role.roleId(), e);
            }
            logger.debug("Role '{}' already exists, skipping creation.", role.roleId());
          }
        });
  }

  private void assignRolesToUsers() {
    final var users = consoleClient.fetchMembers();

    users
        .members()
        .forEach(
            member -> {
              final var email = member.email();

              member.roles().stream()
                  .filter(role -> role != ConsoleClient.Role.IGNORED)
                  .forEach(
                      role -> {
                        final var effectiveRole =
                            (role == ConsoleClient.Role.OWNER) ? ConsoleClient.Role.ADMIN : role;

                        final var request =
                            new RoleMemberRequest(effectiveRole.getName(), email, EntityType.USER);

                        totalRoleAssignmentAttempts.incrementAndGet();

                        try {
                          retryOnBackpressure(
                              () -> roleServices.addMember(request).join(),
                              "assigning role '"
                                  + effectiveRole.getName()
                                  + "' to user '"
                                  + email
                                  + "'");
                          assignedRoleCount.incrementAndGet();
                          logger.debug(
                              "Assigned role '{}' to user '{}'", effectiveRole.getName(), email);
                        } catch (final Exception e) {
                          if (!isConflictError(e)) {
                            throw new MigrationException(
                                "Failed to assign role '"
                                    + role.name()
                                    + "' to user '"
                                    + email
                                    + "'",
                                e);
                          }
                          logger.debug(
                              "Role '{}' already assigned to user '{}'. Skipping assignment.",
                              effectiveRole.getName(),
                              email);
                        }
                      });
            });
  }
}
