/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity;

import static io.camunda.migration.identity.config.saas.StaticEntities.IDENTITY_DECISION_DEFINITION_RESOURCE_TYPE;
import static io.camunda.migration.identity.config.saas.StaticEntities.IDENTITY_PROCESS_DEFINITION_RESOURCE_TYPE;

import io.camunda.migration.identity.console.ConsoleClient;
import io.camunda.migration.identity.console.ConsoleClient.Member;
import io.camunda.migration.identity.dto.Authorization;
import io.camunda.migration.identity.midentity.ManagementIdentityClient;
import io.camunda.security.auth.Authentication;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.AuthorizationServices.CreateAuthorizationRequest;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AuthorizationMigrationHandler extends MigrationHandler<Authorization> {
  private static final Logger LOG = LoggerFactory.getLogger(AuthorizationMigrationHandler.class);
  private final AuthorizationServices authorizationService;
  private final ManagementIdentityClient managementIdentityClient;
  private final ConsoleClient consoleClient;

  public AuthorizationMigrationHandler(
      final Authentication authentication,
      final AuthorizationServices authorizationService,
      final ConsoleClient consoleClient,
      final ManagementIdentityClient managementIdentityClient) {
    this.consoleClient = consoleClient;
    this.managementIdentityClient = managementIdentityClient;
    this.authorizationService = authorizationService.withAuthentication(authentication);
  }

  @Override
  protected List<Authorization> fetchBatch(final int page) {
    // The authorizations are fetched without pagination
    return List.of();
  }

  @Override
  protected void process(final List<Authorization> authorizations) {
    createAuthorizations();
  }

  private void createAuthorizations() {
    final var usersEmailByUsername = getUsersEmailByID();
    final var authorizations = managementIdentityClient.fetchAuthorizations();
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
            authorizationService.createAuthorization(request).join();
            LOG.debug(
                "Migrating authorization: {} to an Authorization with ownerId: {}",
                authorization,
                request.ownerId());
          } catch (final Exception e) {
            if (!isConflictError(e)) {
              throw new RuntimeException(
                  "Failed to migrate authorization for entity ID: " + authorization.entityId(), e);
            }
            LOG.debug(
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
      LOG.debug("Unknown owner type: {}. Defaulting to UNSPECIFIED.", ownerType);
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
    LOG.debug("Unknown resource type: {}. Defaulting to UNSPECIFIED.", resourceType);
    return AuthorizationResourceType.UNSPECIFIED;
  }

  private Set<PermissionType> convertPermissions(
      final Set<String> permissions, final String resourceType) {
    return switch (resourceType) {
      case IDENTITY_DECISION_DEFINITION_RESOURCE_TYPE -> convertDecisionPermissions(permissions);
      case IDENTITY_PROCESS_DEFINITION_RESOURCE_TYPE -> convertProcessPermissions(permissions);
      default -> {
        LOG.warn("Unknown resource type: {}. Skipping permissions conversion.", resourceType);
        yield Collections.emptySet();
      }
    };
  }

  private Set<PermissionType> convertDecisionPermissions(final Set<String> permissions) {
    final Set<PermissionType> result = new HashSet<>();
    if (permissions.contains("READ")) {
      result.add(PermissionType.READ_DECISION_DEFINITION);
      result.add(PermissionType.READ_DECISION_INSTANCE);
    }
    if (permissions.contains("DELETE")) {
      result.add(PermissionType.DELETE_DECISION_INSTANCE);
    }
    return result;
  }

  private Set<PermissionType> convertProcessPermissions(final Set<String> permissions) {
    final Set<PermissionType> result = new HashSet<>();
    if (permissions.contains("READ")) {
      result.add(PermissionType.READ_PROCESS_DEFINITION);
      result.add(PermissionType.READ_PROCESS_INSTANCE);
    }
    if (permissions.contains("UPDATE_PROCESS_INSTANCE")) {
      result.add(PermissionType.UPDATE_PROCESS_INSTANCE);
    }
    if (permissions.contains("START_PROCESS_INSTANCE")) {
      result.add(PermissionType.CREATE_PROCESS_INSTANCE);
    }
    if (permissions.contains("DELETE") || permissions.contains("DELETE_PROCESS_INSTANCE")) {
      result.add(PermissionType.DELETE_PROCESS_INSTANCE);
    }
    return result;
  }
}
