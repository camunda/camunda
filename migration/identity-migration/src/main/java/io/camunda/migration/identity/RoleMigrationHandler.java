/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity;

import io.camunda.migration.identity.dto.Role;
import io.camunda.security.auth.Authentication;
import io.camunda.service.RoleServices;
import io.camunda.service.RoleServices.CreateRoleRequest;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RoleMigrationHandler extends MigrationHandler<Role> {
  private static final Logger LOG = LoggerFactory.getLogger(RoleMigrationHandler.class);
  private final RoleServices roleServices;
  private final List<CreateRoleRequest> roles =
      List.of(
          new CreateRoleRequest("developer", "Developer", ""),
          new CreateRoleRequest("operationsEngineer", "Operations Engineer", ""),
          new CreateRoleRequest("taskUser", "Task User", ""),
          new CreateRoleRequest("visitor", "Visitor", ""));

  public RoleMigrationHandler(
      final RoleServices roleServices, final Authentication servicesAuthentication) {
    this.roleServices = roleServices.withAuthentication(servicesAuthentication);
  }

  @Override
  protected List<Role> fetchBatch(final int page) {
    // Roles are created statically
    return List.of();
  }

  @Override
  protected void process(final List<Role> batch) {
    createRoles();
  }

  private void createRoles() {
    roles.forEach(
        role -> {
          try {
            roleServices.createRole(role);
          } catch (final Exception e) {
            if (!isConflictError(e)) {
              throw new RuntimeException("Failed to migrate role with ID: " + role.roleId(), e);
            }
          }
        });
  }
}
