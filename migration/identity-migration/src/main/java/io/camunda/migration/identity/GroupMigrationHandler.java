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
import io.camunda.migration.identity.console.ConsoleClient.Member;
import io.camunda.migration.identity.dto.Group;
import io.camunda.migration.identity.midentity.ManagementIdentityClient;
import io.camunda.security.auth.Authentication;
import io.camunda.service.GroupServices;
import io.camunda.service.GroupServices.GroupDTO;
import io.camunda.service.GroupServices.GroupMemberDTO;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class GroupMigrationHandler extends MigrationHandler<Group> {

  private final ConsoleClient consoleClient;
  private final ManagementIdentityClient managementIdentityClient;
  private final GroupServices groupServices;

  public GroupMigrationHandler(
      final Authentication authentication,
      final ConsoleClient consoleClient,
      final ManagementIdentityClient managementIdentityClient,
      final GroupServices groupServices) {
    this.consoleClient = consoleClient;
    this.managementIdentityClient = managementIdentityClient;
    this.groupServices = groupServices.withAuthentication(authentication);
  }

  @Override
  protected List<Group> fetchBatch(final int page) {
    return managementIdentityClient.fetchGroups(page);
  }

  @Override
  protected void process(final List<Group> batch) {
    final Map<String, String> userIdToEmailMapping =
        consoleClient.fetchMembers().members().stream()
            .collect(Collectors.toMap(Member::userId, Member::email));
    batch.forEach(
        group -> {
          logger.debug("Migrating Group: {}", group);
          final var normalizedGroupId = normalizeGroupID(group);
          try {
            final var groupDTO = new GroupDTO(normalizedGroupId, group.name(), "");
            groupServices.createGroup(groupDTO).join();
          } catch (final Exception e) {
            if (!isConflictError(e)) {
              throw new MigrationException("Failed to migrate group with ID: " + group.id(), e);
            }
          }
          assignUsersToGroup(group.id(), normalizedGroupId, userIdToEmailMapping);
        });
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

  private void assignUsersToGroup(
      final String sourceGroupId,
      final String targetGroupId,
      final Map<String, String> userIdToEmailMapping) {
    final var users = managementIdentityClient.fetchGroupUsers(sourceGroupId);
    users.forEach(
        user -> {
          try {
            final String userEmail =
                Optional.ofNullable(user.getEmail())
                    .orElseGet(() -> userIdToEmailMapping.get(user.getId()));
            if (userEmail == null) {
              logger.warn(
                  "Could not resolve user email for userId: {}, will skip this member.",
                  user.getId());
              return;
            }
            final var groupMember = new GroupMemberDTO(targetGroupId, userEmail, EntityType.USER);
            groupServices.assignMember(groupMember).join();
          } catch (final Exception e) {
            if (!isConflictError(e)) {
              throw new MigrationException(
                  String.format(
                      "Failed to assign user with ID '%s' to group with ID '%s'",
                      user.getEmail(), targetGroupId),
                  e);
            }
          }
        });
  }
}
