/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.handler.sm;

import static io.camunda.migration.identity.MigrationUtil.normalizeID;

import io.camunda.identity.sdk.users.dto.User;
import io.camunda.migration.api.MigrationException;
import io.camunda.migration.identity.client.ManagementIdentityClient;
import io.camunda.migration.identity.config.IdentityMigrationProperties;
import io.camunda.migration.identity.handler.MigrationHandler;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.RoleServices;
import io.camunda.service.RoleServices.RoleMemberRequest;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class UserRoleMigrationHandler extends MigrationHandler<User> {

  private final ManagementIdentityClient managementIdentityClient;
  private final RoleServices roleServices;

  private final AtomicInteger assignedUserCount = new AtomicInteger();
  private final AtomicInteger totalRoleCount = new AtomicInteger();

  public UserRoleMigrationHandler(
      final CamundaAuthentication authentication,
      final ManagementIdentityClient managementIdentityClient,
      final RoleServices roleServices,
      final IdentityMigrationProperties migrationProperties) {
    super(migrationProperties.getBackpressureDelay());
    this.managementIdentityClient = managementIdentityClient;
    this.roleServices = roleServices.withAuthentication(authentication);
  }

  @Override
  protected List<User> fetchBatch(final int page) {
    return managementIdentityClient.fetchUsers(page);
  }

  @Override
  protected void process(final List<User> batch) {
    batch.forEach(
        user -> {
          final var userId = user.getEmail();
          final var userRoles = managementIdentityClient.fetchUserRoles(user.getId());
          totalRoleCount.addAndGet(userRoles.size());

          userRoles.forEach(
              role -> {
                try {
                  final var roleId = normalizeID(role.name());
                  logger.debug("Assigning role '{}' to user '{}'", roleId, userId);
                  final var roleMember = new RoleMemberRequest(roleId, userId, EntityType.USER);
                  retryOnBackpressure(
                      () -> roleServices.addMember(roleMember).join(),
                      String.format("Failed to assign user '%s' to role '%s'", userId, roleId));
                  assignedUserCount.incrementAndGet();
                } catch (final Exception e) {
                  if (!isConflictError(e)) {
                    throw new MigrationException("Failed to assign user: " + userId, e);
                  }
                  logger.debug(
                      "User with ID '{}' is already assigned to role with ID '{}', skipping assignation.",
                      userId,
                      role.name());
                }
              });
        });
  }

  @Override
  protected void logSummary() {
    logger.info(
        "User Role membership migration completed: Assigned {} roles out of {} roles.",
        assignedUserCount.get(),
        totalRoleCount.get());
  }
}
