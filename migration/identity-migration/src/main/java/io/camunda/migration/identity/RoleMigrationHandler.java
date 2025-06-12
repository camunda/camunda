/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity;

import io.camunda.migration.api.MigrationException;
import io.camunda.migration.identity.console.ConsoleClient;
import io.camunda.migration.identity.dto.Role;
import io.camunda.security.auth.Authentication;
import io.camunda.service.RoleServices;
import io.camunda.service.RoleServices.CreateRoleRequest;
import io.camunda.service.RoleServices.RoleMemberRequest;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;

public class RoleMigrationHandler extends MigrationHandler<Role> {
  private final RoleServices roleServices;
  private final ConsoleClient consoleClient;
  private final List<CreateRoleRequest> roles =
      List.of(
          new CreateRoleRequest("developer", "Developer", ""),
          new CreateRoleRequest("operationsengineer", "Operations Engineer", ""),
          new CreateRoleRequest("taskuser", "Task User", ""),
          new CreateRoleRequest("visitor", "Visitor", ""));

  public RoleMigrationHandler(
      final RoleServices roleServices,
      final Authentication servicesAuthentication,
      final ConsoleClient consoleClient) {
    this.roleServices = roleServices.withAuthentication(servicesAuthentication);
    this.consoleClient = consoleClient;
  }

  @Override
  protected List<Role> fetchBatch(final int page) {
    // Roles are created statically
    return List.of();
  }

  @Override
  protected void process(final List<Role> batch) {
    createRoles();
    assignRolesToUsers();
  }

  private void createRoles() {
    roles.forEach(
        role -> {
          try {
            roleServices.createRole(role).join();
          } catch (final Exception e) {
            if (!isConflictError(e)) {
              throw new MigrationException("Failed to migrate role with ID: " + role.roleId(), e);
            }
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

                        try {
                          roleServices.addMember(request).join();
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
                        }
                      });
            });
  }
}
