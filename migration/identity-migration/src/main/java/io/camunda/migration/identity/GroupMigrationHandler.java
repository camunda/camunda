/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity;

import io.camunda.migration.identity.dto.Group;
import io.camunda.migration.identity.midentity.ManagementIdentityClient;
import io.camunda.security.auth.Authentication;
import io.camunda.service.GroupServices;
import io.camunda.service.GroupServices.GroupDTO;
import io.camunda.service.GroupServices.GroupMemberDTO;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;

public class GroupMigrationHandler extends MigrationHandler<Group> {

  private final ManagementIdentityClient managementIdentityClient;
  private final GroupServices groupServices;

  public GroupMigrationHandler(
      final Authentication authentication,
      final ManagementIdentityClient managementIdentityClient,
      final GroupServices groupServices) {
    this.managementIdentityClient = managementIdentityClient;
    this.groupServices = groupServices.withAuthentication(authentication);
  }

  @Override
  protected List<Group> fetchBatch(final int page) {
    return managementIdentityClient.fetchGroups(page);
  }

  @Override
  protected void process(final List<Group> batch) {
    batch.forEach(this::processTask);
  }

  private void processTask(final Group group) {
    try {
      final var normalizedGroupId = normalizeGroupID(group);
      final var groupDTO = new GroupDTO(normalizedGroupId, group.name(), "");
      groupServices.createGroup(groupDTO);
      assignUsersToGroup(group.id(), normalizedGroupId);
    } catch (final Exception e) {
      if (!isConflictError(e)) {
        throw new RuntimeException("Failed to migrate group with ID: " + group.id(), e);
      }
    }
  }

  // Normalizes the group ID to ensure it meets the requirements for a valid group ID.
  // For SaaS the group ID is derived from the group name, because in the old identity
  // management system the group ID was generated internally.
  private String normalizeGroupID(final Group group) {
    if (group.name() == null || group.name().isEmpty()) {
      return group.id();
    }
    final String groupName = group.name();

    String normalizedId =
        groupName.toLowerCase().replaceAll("[^a-z0-9_@.-]", "_"); // Replace disallowed characters

    if (normalizedId.length() > 256) {
      normalizedId = normalizedId.substring(0, 256);
    }
    return normalizedId;
  }

  private void assignUsersToGroup(final String sourceGroupId, final String targetGroupId) {
    final var users = managementIdentityClient.fetchGroupUsers(sourceGroupId);
    users.forEach(
        user -> {
          try {
            final var groupMember =
                new GroupMemberDTO(targetGroupId, user.getEmail(), EntityType.USER);
            groupServices.assignMember(groupMember);
          } catch (final Exception e) {
            if (!isConflictError(e)) {
              throw new RuntimeException(
                  String.format(
                      "Failed to assign user with ID '%s' to group with ID '%s'",
                      user.getEmail(), targetGroupId),
                  e);
            }
          }
        });
  }
}
