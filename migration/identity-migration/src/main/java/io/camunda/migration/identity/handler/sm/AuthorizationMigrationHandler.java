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
import static io.camunda.migration.identity.config.saas.StaticEntities.IDENTITY_DECISION_DEFINITION_RESOURCE_TYPE;
import static io.camunda.migration.identity.config.saas.StaticEntities.IDENTITY_PROCESS_DEFINITION_RESOURCE_TYPE;

import io.camunda.identity.sdk.users.dto.User;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.lang3.NotImplementedException;

public class AuthorizationMigrationHandler extends MigrationHandler<Authorization> {

  private final AuthorizationServices authorizationService;
  private final ManagementIdentityClient managementIdentityClient;

  private final AtomicInteger createdAuthorizationsCount = new AtomicInteger();
  private final AtomicInteger skippedAuthorizationsCount = new AtomicInteger();
  private final AtomicInteger totalAuthorizationsCount = new AtomicInteger();

  public AuthorizationMigrationHandler(
      final CamundaAuthentication authentication,
      final AuthorizationServices authorizationService,
      final ManagementIdentityClient managementIdentityClient,
      final IdentityMigrationProperties migrationProperties) {
    super(migrationProperties.getBackpressureDelay());
    this.managementIdentityClient = managementIdentityClient;
    this.authorizationService = authorizationService.withAuthentication(authentication);
  }

  @Override
  protected List<Authorization> fetchBatch(final int page) {
    return List.of();
  }

  @Override
  protected void process(final List<Authorization> batch) {
    final var usersEmailByUsername = getAllUsers();

    try {
      usersEmailByUsername.forEach(
          user -> {
            final List<Authorization> authorizations =
                managementIdentityClient.fetchUserAuthorizations(user.getId());

            authorizations.forEach(
                authorization -> {
                  final var request =
                      new CreateAuthorizationRequest(
                          user.getEmail(),
                          AuthorizationOwnerType.USER,
                          authorization.resourceKey(),
                          convertResourceType(authorization.resourceType()),
                          convertPermissions(
                              authorization.permissions(), authorization.resourceType()));
                  try {
                    retryOnBackpressure(
                        () -> authorizationService.createAuthorization(request).join(),
                        "Failed to create authorization for entity with ID: "
                            + authorization.entityId()
                            + " and owner with ID: "
                            + user.getEmail());
                    createdAuthorizationsCount.incrementAndGet();
                    logger.debug(
                        "Migrated authorization: {} to an Authorization with ownerId: {}",
                        authorization,
                        request.ownerId());
                  } catch (final Exception e) {
                    if (!isConflictError(e)) {
                      throw new RuntimeException(
                          "Failed to migrate authorization for entity with ID: "
                              + authorization.entityId()
                              + " and owner with ID: "
                              + user.getEmail(),
                          e);
                    }
                    skippedAuthorizationsCount.incrementAndGet();
                    logger.debug(
                        "Authorization already exists for entity with ID: {} and owner with ID {}. Skipping creation.",
                        authorization.entityId(),
                        user.getEmail());
                  }
                });
          });
    } catch (final NotImplementedException e) {
      logger.warn(
          "Authorizations endpoint is not available, this indicates resource authorizations are not enabled in identity, skipping.");
    }
  }

  @Override
  protected void logSummary() {
    logger.info(
        "Authorization migration complete: Created {} out of {} authorizations. Skipped {} as they already exist.",
        createdAuthorizationsCount.get(),
        totalAuthorizationsCount.get(),
        skippedAuthorizationsCount.get());
  }

  public Set<User> getAllUsers() {
    final Set<User> allUsers = new HashSet<>();
    int page = 0;
    List<User> users;

    do {
      users = managementIdentityClient.fetchUsers(page);
      logger.debug("Fetched {} users from page {}", users.size(), page);
      allUsers.addAll(users);
      page++;
    } while (!users.isEmpty());

    return allUsers;
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
