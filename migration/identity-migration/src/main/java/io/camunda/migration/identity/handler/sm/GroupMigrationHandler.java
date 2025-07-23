/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.handler.sm;

import static io.camunda.migration.identity.MigrationUtil.convertDecisionPermissions;
import static io.camunda.migration.identity.MigrationUtil.convertProcessPermissions;
import static io.camunda.migration.identity.MigrationUtil.normalizeGroupID;
import static io.camunda.migration.identity.MigrationUtil.normalizeID;
import static io.camunda.migration.identity.config.saas.StaticEntities.IDENTITY_DECISION_DEFINITION_RESOURCE_TYPE;
import static io.camunda.migration.identity.config.saas.StaticEntities.IDENTITY_PROCESS_DEFINITION_RESOURCE_TYPE;

import io.camunda.migration.api.MigrationException;
import io.camunda.migration.identity.client.ManagementIdentityClient;
import io.camunda.migration.identity.dto.Authorization;
import io.camunda.migration.identity.dto.Group;
import io.camunda.migration.identity.handler.MigrationHandler;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.AuthorizationServices.CreateAuthorizationRequest;
import io.camunda.service.GroupServices;
import io.camunda.service.GroupServices.GroupDTO;
import io.camunda.service.GroupServices.GroupMemberDTO;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.EntityType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class GroupMigrationHandler extends MigrationHandler<Group> {

  private final ManagementIdentityClient managementIdentityClient;
  private final GroupServices groupServices;
  private final AuthorizationServices authorizationServices;

  private final AtomicInteger createdGroupCount = new AtomicInteger();
  private final AtomicInteger assignedUserCount = new AtomicInteger();
  private final AtomicInteger assignedRoleCount = new AtomicInteger();
  private final AtomicInteger totalGroupCount = new AtomicInteger();
  private final AtomicInteger totalUserAssignmentAttempts = new AtomicInteger();
  private final AtomicInteger totalRoleAssignmentAttempts = new AtomicInteger();

  public GroupMigrationHandler(
      final ManagementIdentityClient managementIdentityClient,
      final GroupServices groupServices,
      final AuthorizationServices authorizationServices,
      final CamundaAuthentication authentication) {
    this.managementIdentityClient = managementIdentityClient;
    this.groupServices = groupServices.withAuthentication(authentication);
    this.authorizationServices = authorizationServices.withAuthentication(authentication);
  }

  @Override
  protected List<Group> fetchBatch(final int page) {
    return managementIdentityClient.fetchGroups(page);
  }

  @Override
  protected void process(final List<Group> batch) {
    totalGroupCount.addAndGet(batch.size());

    batch.forEach(
        group -> {
          final var normalizedGroupId = normalizeGroupID(group);
          logger.debug(
              "Migrating Group: {} to a Group with the identifier: {}.", group, normalizedGroupId);
          try {
            final var groupDTO = new GroupDTO(normalizedGroupId, group.name(), "");
//            groupServices.createGroup(groupDTO).join();
            createdGroupCount.incrementAndGet();
          } catch (final Exception e) {
            if (!isConflictError(e)) {
              throw new MigrationException("Failed to migrate group with ID: " + group.id(), e);
            }
            logger.debug("Group with ID '{}' already exists, skipping creation.", group.id());
          }
//          assignUsersToGroup(group.id(), normalizedGroupId);
          assignRolesToGroup(group.id(), normalizedGroupId);
          createAuthorizationsForGroup(group.id(), normalizedGroupId);
        });
  }

  @Override
  protected void logSummary() {
    logger.info(
        "Group migration completed: Created {} out of {} groups, the remaining existed already. Assigned {} users out of {} attempted, the remaining were already assigned. Assigned {} roles out of {} attempted, the remaining were already assigned.",
        createdGroupCount.get(),
        totalGroupCount.get(),
        assignedUserCount.get(),
        totalUserAssignmentAttempts.get(),
        assignedRoleCount.get(),
        totalRoleAssignmentAttempts.get());
  }

  private void assignUsersToGroup(final String groupId, final String targetGroupId) {
    final var users = managementIdentityClient.fetchGroupUsers(groupId);
    totalUserAssignmentAttempts.addAndGet(users.size());

    users.forEach(
        user -> {
          try {
            final var username = user.getUsername();
            logger.debug(
                "Assigning User: {} with username: {} to Group: {}",
                user.getId(),
                username,
                targetGroupId);
            final var groupMember = new GroupMemberDTO(targetGroupId, username, EntityType.USER);
            groupServices.assignMember(groupMember).join();
            assignedUserCount.incrementAndGet();
          } catch (final Exception e) {
            if (!isConflictError(e)) {
              throw new MigrationException(
                  String.format(
                      "Failed to assign user with ID '%s' to group with ID '%s'",
                      user.getUsername(), targetGroupId),
                  e);
            }
            logger.debug(
                "User with ID '{}' already assigned to group '{}', skipping assignment.",
                user.getUsername(),
                targetGroupId);
          }
        });
  }

  private void assignRolesToGroup(final String groupId, final String targetGroupId) {
    final var roles = managementIdentityClient.fetchGroupRoles(groupId);
    totalRoleAssignmentAttempts.addAndGet(roles.size());

    roles.forEach(
        role -> {
          try {
            final var normalizedRoleId = normalizeID(role.name());
            logger.debug("Assigning Role: {} to Group: {}", normalizedRoleId, targetGroupId);
            final var groupMember =
                new GroupMemberDTO(targetGroupId, normalizedRoleId, EntityType.ROLE);
            groupServices.assignMember(groupMember).join();
            assignedRoleCount.incrementAndGet();
          } catch (final Exception e) {
            if (!isConflictError(e)) {
              throw new MigrationException(
                  String.format(
                      "Failed to assign role '%s' to group with ID '%s'",
                      role.name(), targetGroupId),
                  e);
            }
            logger.debug(
                "Role '{}' already assigned to group '{}', skipping assignment.",
                role.name(),
                targetGroupId);
          }
        });
  }

  private void createAuthorizationsForGroup(final String groupId, final String targetGroupId) {
    final List<Authorization> authorizations;
    try {
      authorizations = managementIdentityClient.fetchGroupAuthorizations(groupId);
    } catch (final Exception e) {
      if (!isNotImplementedError(e)) {
        throw new MigrationException(
            String.format("Failed to fetch authorizations for group with ID '%s'", groupId), e);
      }
      logger.warn("Authorization endpoint is not available, skipping.");
      return;
    }

    authorizations.forEach(
        authorization -> {
          final var request =
              new CreateAuthorizationRequest(
                  targetGroupId,
                  AuthorizationOwnerType.GROUP,
                  authorization.resourceKey(),
                  convertResourceType(authorization.resourceType()),
                  convertPermissions(authorization.permissions(), authorization.resourceType()));
          try {
            authorizationServices.createAuthorization(request).join();
            logger.debug(
                "Authorization created for group '{}' with permissions '{}'.",
                targetGroupId,
                request.permissionTypes());
          } catch (final Exception e) {
            if (!isConflictError(e)) {
              throw new MigrationException(
                  String.format(
                      "Failed to create authorization for group '%s' with permissions '%s'",
                      targetGroupId, request.permissionTypes()),
                  e);
            }
            logger.debug(
                "Authorization for group '{}' with permissions '{}' already exists, skipping creation.",
                targetGroupId,
                authorization.permissions());
          }
        });
  }

  private AuthorizationResourceType convertResourceType(final String resourceType) {
    return switch (resourceType) {
      case IDENTITY_PROCESS_DEFINITION_RESOURCE_TYPE ->
          AuthorizationResourceType.PROCESS_DEFINITION;
      case IDENTITY_DECISION_DEFINITION_RESOURCE_TYPE ->
          AuthorizationResourceType.DECISION_DEFINITION;
      default -> {
        logger.debug("Unknown resource type: {}. Defaulting to UNSPECIFIED.", resourceType);
        yield AuthorizationResourceType.UNSPECIFIED;
      }
    };
  }

  private Set<PermissionType> convertPermissions(
      final Set<String> permissions, final String resourceType) {
    return switch (resourceType) {
      case IDENTITY_DECISION_DEFINITION_RESOURCE_TYPE -> convertDecisionPermissions(permissions);
      case IDENTITY_PROCESS_DEFINITION_RESOURCE_TYPE -> convertProcessPermissions(permissions);
      default -> {
        logger.warn("Unknown resource type: {}. Skipping permissions conversion.", resourceType);
        yield Collections.emptySet();
      }
    };
  }
}
