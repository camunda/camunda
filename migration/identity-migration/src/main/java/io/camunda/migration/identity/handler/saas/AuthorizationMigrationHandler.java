/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.handler.saas;

import static io.camunda.migration.identity.MigrationUtil.convertDecisionPermissions;
import static io.camunda.migration.identity.MigrationUtil.convertProcessPermissions;
import static io.camunda.migration.identity.config.saas.StaticEntities.IDENTITY_DECISION_DEFINITION_RESOURCE_TYPE;
import static io.camunda.migration.identity.config.saas.StaticEntities.IDENTITY_PROCESS_DEFINITION_RESOURCE_TYPE;

import io.camunda.migration.identity.client.ConsoleClient;
import io.camunda.migration.identity.client.ConsoleClient.Member;
import io.camunda.migration.identity.client.ManagementIdentityClient;
import io.camunda.migration.identity.config.IdentityMigrationProperties;
import io.camunda.migration.identity.dto.Authorization;
import io.camunda.migration.identity.handler.MigrationHandler;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.AuthorizationServices.CreateAuthorizationRequest;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class AuthorizationMigrationHandler extends MigrationHandler<Authorization> {
  private final AuthorizationServices authorizationService;
  private final ManagementIdentityClient managementIdentityClient;
  private final ConsoleClient consoleClient;

  private final AtomicInteger createdAuthorizationsCount = new AtomicInteger();
  private final AtomicInteger skippedAuthorizationsCount = new AtomicInteger();
  private final AtomicInteger totalAuthorizationsCount = new AtomicInteger();

  public AuthorizationMigrationHandler(
      final CamundaAuthentication authentication,
      final AuthorizationServices authorizationService,
      final ConsoleClient consoleClient,
      final ManagementIdentityClient managementIdentityClient,
      final IdentityMigrationProperties migrationProperties) {
    super(migrationProperties.getBackpressureDelay());
    this.consoleClient = consoleClient;
    this.managementIdentityClient = managementIdentityClient;
    this.authorizationService = authorizationService.withAuthentication(authentication);
  }

  @Override
  protected List<Authorization> fetchBatch(final int page) {
    return List.of();
  }

  @Override
  protected void process(final List<Authorization> authorizations) {
    createAuthorizations();
  }

  @Override
  protected void logSummary() {
    logger.info(
        "Authorization migration complete: Created {} out of {} authorizations. Skipped {} as they already exist.",
        createdAuthorizationsCount.get(),
        totalAuthorizationsCount.get(),
        skippedAuthorizationsCount.get());
  }

  private void createAuthorizations() {
    final var usersEmailByUsername = getUsersEmailByID();
    final var authorizations = managementIdentityClient.fetchAuthorizations();

    totalAuthorizationsCount.set(authorizations.size());

    authorizations.forEach(
        authorization -> {
          final var request =
              new CreateAuthorizationRequest(
                  getOwnerId(authorization.entityId(), usersEmailByUsername),
                  convertOwnerType(authorization.entityType()),
                  authorization.resourceKey(),
                  convertResourceType(authorization.resourceType()),
                  convertPermissions(authorization.permissions(), authorization.resourceType()));
          try {
            retryOnBackpressure(
                () -> authorizationService.createAuthorization(request).join(),
                "creating authorization for entity ID: " + authorization.entityId());
            createdAuthorizationsCount.incrementAndGet();
            logger.debug(
                "Migrating authorization: {} to an Authorization with ownerId: {}",
                authorization,
                request.ownerId());
          } catch (final Exception e) {
            if (!isConflictError(e)) {
              throw new RuntimeException(
                  "Failed to migrate authorization for entity ID: " + authorization.entityId(), e);
            }
            skippedAuthorizationsCount.incrementAndGet();
            logger.debug(
                "Authorization already exists for entity ID: {}. Skipping creation.",
                authorization.entityId());
          }
        });
  }

  // Fetches all users and their emails from console
  private Map<String, String> getUsersEmailByID() {
    return consoleClient.fetchMembers().members().stream()
        .collect(Collectors.toMap(Member::userId, Member::email));
  }

  private String getOwnerId(
      final String identityEntityId, final Map<String, String> usersEmailByUsername) {
    if (usersEmailByUsername.containsKey(identityEntityId)) {
      return usersEmailByUsername.get(identityEntityId);
    }
    return identityEntityId;
  }

  private AuthorizationOwnerType convertOwnerType(final String ownerType) {
    try {
      return AuthorizationOwnerType.valueOf(ownerType);
    } catch (final IllegalArgumentException e) {
      logger.debug("Unknown owner type: {}. Defaulting to UNSPECIFIED.", ownerType);
      return AuthorizationOwnerType.UNSPECIFIED;
    }
  }

  private AuthorizationResourceType convertResourceType(final String resourceType) {
    if (IDENTITY_PROCESS_DEFINITION_RESOURCE_TYPE.equalsIgnoreCase(resourceType)) {
      return AuthorizationResourceType.PROCESS_DEFINITION;
    }
    if (IDENTITY_DECISION_DEFINITION_RESOURCE_TYPE.equalsIgnoreCase(resourceType)) {
      return AuthorizationResourceType.DECISION_DEFINITION;
    }
    logger.debug("Unknown resource type: {}. Defaulting to UNSPECIFIED.", resourceType);
    return AuthorizationResourceType.UNSPECIFIED;
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
