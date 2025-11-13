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
import static io.camunda.migration.identity.MigrationUtil.normalizeID;
import static io.camunda.migration.identity.config.saas.StaticEntities.IDENTITY_DECISION_DEFINITION_RESOURCE_TYPE;
import static io.camunda.migration.identity.config.saas.StaticEntities.IDENTITY_PROCESS_DEFINITION_RESOURCE_TYPE;

import io.camunda.migration.api.MigrationException;
import io.camunda.migration.identity.client.ManagementIdentityClient;
import io.camunda.migration.identity.config.IdentityMigrationProperties;
import io.camunda.migration.identity.dto.Authorization;
import io.camunda.migration.identity.dto.Group;
import io.camunda.migration.identity.handler.MigrationHandler;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.AuthorizationServices.CreateAuthorizationRequest;
import io.camunda.service.RoleServices;
import io.camunda.service.RoleServices.RoleMemberRequest;
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
  private final RoleServices roleServices;
  private final AuthorizationServices authorizationServices;

  private final AtomicInteger createdGroupAuthorizationCount = new AtomicInteger();
  private final AtomicInteger totalGroupAuthorizationCount = new AtomicInteger();
  private final AtomicInteger createdGroupRoleMembershipCount = new AtomicInteger();
  private final AtomicInteger totalGroupRoleMembershipCount = new AtomicInteger();

  public GroupMigrationHandler(
      final ManagementIdentityClient managementIdentityClient,
      final AuthorizationServices authorizationServices,
      final RoleServices roleServices,
      final CamundaAuthentication authentication,
      final IdentityMigrationProperties migrationProperties) {
    super(migrationProperties.getBackpressureDelay());
    this.managementIdentityClient = managementIdentityClient;
    this.roleServices = roleServices.withAuthentication(authentication);
    this.authorizationServices = authorizationServices.withAuthentication(authentication);
  }

  @Override
  protected List<Group> fetchBatch(final int page) {
    return managementIdentityClient.fetchGroups(page);
  }

  @Override
  protected void process(final List<Group> batch) {
    batch.forEach(
        group -> {
          createAuthorizationsForGroup(group);
          createGroupRoleMembership(group);
        });
  }

  @Override
  protected void logSummary() {
    logger.info(
        "Group migration completed: {} authorizations created out of {} total, {} role memberships created out of {} total.",
        createdGroupAuthorizationCount.get(),
        totalGroupAuthorizationCount.get(),
        createdGroupRoleMembershipCount,
        totalGroupRoleMembershipCount);
  }

  private void createGroupRoleMembership(final Group group) {
    final var groupRoles = managementIdentityClient.fetchGroupRoles(group.id());
    totalGroupRoleMembershipCount.addAndGet(groupRoles.size());

    groupRoles.forEach(
        role -> {
          try {
            final var roleId = normalizeID(role.name());
            logger.debug("Assigning role '{}' to group '{}'", roleId, group.name());
            final var roleMember = new RoleMemberRequest(roleId, group.name(), EntityType.GROUP);
            retryOnBackpressure(
                () -> roleServices.addMember(roleMember).join(),
                String.format("Failed to assign group '%s' to role '%s'", group.name(), roleId));
            createdGroupRoleMembershipCount.incrementAndGet();
          } catch (final Exception e) {
            if (!isConflictError(e)) {
              throw new MigrationException("Failed to assign group: " + group.name(), e);
            }
            logger.debug(
                "Group with name '{}' is already assigned to role with ID '{}', skipping assignation.",
                group.name(),
                role.name());
          }
        });
  }

  private void createAuthorizationsForGroup(final Group group) {
    final List<Authorization> authorizations;
    try {
      authorizations = managementIdentityClient.fetchGroupAuthorizations(group.id());
      totalGroupAuthorizationCount.set(authorizations.size());
    } catch (final Exception e) {
      if (!isNotImplementedError(e)) {
        throw new MigrationException(
            String.format("Failed to fetch authorizations for group with ID '%s'", group.id()), e);
      }
      logger.warn("Authorization endpoint is not available, skipping.");
      return;
    }

    authorizations.forEach(
        authorization -> {
          final var request =
              new CreateAuthorizationRequest(
                  group.name(),
                  AuthorizationOwnerType.GROUP,
                  authorization.resourceKey(),
                  convertResourceType(authorization.resourceType()),
                  convertPermissions(authorization.permissions(), authorization.resourceType()));
          try {
            retryOnBackpressure(
                () -> authorizationServices.createAuthorization(request).join(),
                "Failed to create authorization for group '"
                    + group.name()
                    + "' with permissions '"
                    + request.permissionTypes()
                    + "'");
            createdGroupAuthorizationCount.incrementAndGet();
            logger.debug(
                "Authorization created for group '{}' with permissions '{}'.",
                group.name(),
                request.permissionTypes());
          } catch (final Exception e) {
            if (!isConflictError(e)) {
              throw new MigrationException(
                  String.format(
                      "Failed to create authorization for group '%s' with permissions '%s'",
                      group.name(), request.permissionTypes()),
                  e);
            }
            logger.debug(
                "Authorization for group '{}' with permissions '{}' already exists, skipping creation.",
                group.name(),
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
