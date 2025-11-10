/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.handler.sm;

import static io.camunda.migration.identity.MigrationUtil.normalizeGroupID;
import static io.camunda.migration.identity.MigrationUtil.normalizeID;

import io.camunda.migration.api.MigrationException;
import io.camunda.migration.identity.client.ManagementIdentityClient;
import io.camunda.migration.identity.config.IdentityMigrationProperties;
import io.camunda.migration.identity.dto.Group;
import io.camunda.migration.identity.handler.MigrationHandler;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.RoleServices;
import io.camunda.service.RoleServices.RoleMemberRequest;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class GroupRoleMigrationHandler extends MigrationHandler<Group> {

  private final ManagementIdentityClient managementIdentityClient;
  private final RoleServices roleServices;

  private final AtomicInteger assignedGroupCount = new AtomicInteger();
  private final AtomicInteger totalRoleCount = new AtomicInteger();

  public GroupRoleMigrationHandler(
      final ManagementIdentityClient managementIdentityClient,
      final RoleServices roleServices,
      final CamundaAuthentication authentication,
      final IdentityMigrationProperties migrationProperties) {
    super(migrationProperties.getBackpressureDelay());
    this.managementIdentityClient = managementIdentityClient;
    this.roleServices = roleServices.withAuthentication(authentication);
  }

  @Override
  protected List<Group> fetchBatch(final int page) {
    return managementIdentityClient.fetchGroups(page);
  }

  @Override
  protected void process(final List<Group> batch) {
    batch.forEach(
        group -> {
          final var groupRoles = managementIdentityClient.fetchGroupRoles(group.id());
          totalRoleCount.addAndGet(groupRoles.size());

          final var normalizedGroupId = normalizeGroupID(group);
          groupRoles.forEach(
              role -> {
                try {
                  final var roleId = normalizeID(role.name());
                  logger.debug("Assigning role '{}' to group '{}'", roleId, normalizedGroupId);
                  final var roleMember =
                      new RoleMemberRequest(roleId, normalizedGroupId, EntityType.GROUP);
                  retryOnBackpressure(
                      () -> roleServices.addMember(roleMember).join(),
                      String.format(
                          "Failed to assign group '%s' to role '%s'", normalizedGroupId, roleId));
                  assignedGroupCount.incrementAndGet();
                } catch (final Exception e) {
                  if (!isConflictError(e)) {
                    throw new MigrationException("Failed to assign group: " + normalizedGroupId, e);
                  }
                  logger.debug(
                      "Group with ID '{}' is already assigned to role with ID '{}', skipping assignation.",
                      normalizedGroupId,
                      role.name());
                }
              });
        });
  }

  @Override
  protected void logSummary() {
    logger.info(
        "Group Role membership migration completed: Assigned {} roles out of {} roles.",
        assignedGroupCount.get(),
        totalRoleCount.get());
  }
}
