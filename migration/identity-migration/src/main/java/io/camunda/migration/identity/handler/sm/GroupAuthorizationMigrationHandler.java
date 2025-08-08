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
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class GroupAuthorizationMigrationHandler extends MigrationHandler<Group> {

  private final ManagementIdentityClient managementIdentityClient;
  private final AuthorizationServices authorizationServices;

  private final AtomicInteger createdGroupAuthorizationCount = new AtomicInteger();
  private final AtomicInteger totalGroupAuthorizationCount = new AtomicInteger();

  public GroupAuthorizationMigrationHandler(
      final ManagementIdentityClient managementIdentityClient,
      final AuthorizationServices authorizationServices,
      final CamundaAuthentication authentication,
      final IdentityMigrationProperties migrationProperties) {
    super(migrationProperties.getBackpressureDelay());
    this.managementIdentityClient = managementIdentityClient;
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
          final var normalizedGroupId = normalizeGroupID(group);
          createAuthorizationsForGroup(group.id(), normalizedGroupId);
        });
  }

  @Override
  protected void logSummary() {
    logger.info(
        "Group Authorization migration completed: {} authorizations created out of {} total.",
        createdGroupAuthorizationCount.get(),
        totalGroupAuthorizationCount.get());
  }

  private void createAuthorizationsForGroup(final String groupId, final String targetGroupId) {
    final List<Authorization> authorizations;
    try {
      authorizations = managementIdentityClient.fetchGroupAuthorizations(groupId);
      totalGroupAuthorizationCount.set(authorizations.size());
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
            retryOnBackpressure(
                () -> authorizationServices.createAuthorization(request).join(),
                "Failed to create authorization for group '"
                    + targetGroupId
                    + "' with permissions '"
                    + request.permissionTypes()
                    + "'");
            createdGroupAuthorizationCount.incrementAndGet();
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
