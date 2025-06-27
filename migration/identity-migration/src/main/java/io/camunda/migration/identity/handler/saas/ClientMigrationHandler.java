/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.handler.saas;

import static io.camunda.migration.identity.config.saas.StaticEntities.getOperateClientPermissions;
import static io.camunda.migration.identity.config.saas.StaticEntities.getTasklistClientPermissions;
import static io.camunda.migration.identity.config.saas.StaticEntities.getZeebeClientPermissions;

import io.camunda.migration.api.MigrationException;
import io.camunda.migration.identity.client.ConsoleClient;
import io.camunda.migration.identity.client.ConsoleClient.Client;
import io.camunda.migration.identity.client.ConsoleClient.Members;
import io.camunda.migration.identity.client.ConsoleClient.Permission;
import io.camunda.migration.identity.handler.MigrationHandler;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.AuthorizationServices.CreateAuthorizationRequest;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import io.camunda.zeebe.protocol.record.value.AuthorizationResourceType;
import io.camunda.zeebe.protocol.record.value.PermissionType;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class ClientMigrationHandler extends MigrationHandler<Members> {

  final ConsoleClient consoleClient;
  final AuthorizationServices authorizationServices;

  private final AtomicInteger totalClientsProcessed = new AtomicInteger();
  private final AtomicInteger createdAuthorizations = new AtomicInteger();
  private final AtomicInteger skippedAuthorizations = new AtomicInteger();

  public ClientMigrationHandler(
      final ConsoleClient consoleClient,
      final AuthorizationServices authorizationServices,
      final CamundaAuthentication servicesAuthentication) {
    this.consoleClient = consoleClient;
    this.authorizationServices = authorizationServices.withAuthentication(servicesAuthentication);
  }

  @Override
  protected List<Members> fetchBatch(final int page) {
    return List.of();
  }

  @Override
  protected void process(final List<Members> batch) {
    final var clientPermissions =
        consoleClient.fetchMembers().clients().stream()
            .collect(Collectors.toMap(Client::clientId, Client::permissions));

    clientPermissions.forEach(this::handlePermission);
  }

  @Override
  protected void logSummary() {
    logger.info(
        "Client migration complete: Created {} authorizations out of {} clients. Skipped {} as they already exist.",
        createdAuthorizations.get(),
        totalClientsProcessed.get(),
        skippedAuthorizations.get());
  }

  private void handlePermission(final String clientId, final List<Permission> permissions) {
    totalClientsProcessed.incrementAndGet();

    final var combinedPermissions = getCombinedPermissions(clientId, permissions);
    if (combinedPermissions.isEmpty()) {
      logger.debug("No permissions to migrate for client: {}", clientId);
      return;
    }
    for (final CreateAuthorizationRequest request : combinedPermissions) {
      try {
        authorizationServices.createAuthorization(request).join();
        createdAuthorizations.incrementAndGet();
        logger.debug(
            "Migrated client permission with owner ID: {} and resource type: {}",
            request.ownerId(),
            request.resourceType());
      } catch (final Exception e) {
        if (!isConflictError(e)) {
          throw new MigrationException(
              String.format("Failed to migrate client permissions for client ID: %s", clientId), e);
        }
        skippedAuthorizations.incrementAndGet();
        logger.debug(
            "Client authorization with owner ID: {} and resource type: {} already exists, skipping creation.",
            request.ownerId(),
            request.resourceType());
      }
    }
  }

  public static List<CreateAuthorizationRequest> getCombinedPermissions(
      final String clientId, final List<Permission> clientTypes) {

    // Key used to group permission sets
    record PermissionKey(
        String ownerId,
        AuthorizationOwnerType ownerType,
        String resourceId,
        AuthorizationResourceType resourceType) {}

    final Map<PermissionKey, Set<PermissionType>> permissionMap = new HashMap<>();

    final List<CreateAuthorizationRequest> allPermissions = new ArrayList<>();

    if (clientTypes.contains(Permission.ZEEBE)) {
      allPermissions.addAll(getZeebeClientPermissions(clientId));
    }
    if (clientTypes.contains(Permission.OPERATE)) {
      allPermissions.addAll(getOperateClientPermissions(clientId));
    }
    if (clientTypes.contains(Permission.TASKLIST)) {
      allPermissions.addAll(getTasklistClientPermissions(clientId));
    }

    for (final CreateAuthorizationRequest request : allPermissions) {
      final PermissionKey key =
          new PermissionKey(
              request.ownerId(), request.ownerType(), request.resourceId(), request.resourceType());

      permissionMap.merge(
          key,
          new HashSet<>(request.permissionTypes()),
          (existing, incoming) -> {
            existing.addAll(incoming);
            return existing;
          });
    }

    return permissionMap.entrySet().stream()
        .map(
            entry ->
                new CreateAuthorizationRequest(
                    entry.getKey().ownerId(),
                    entry.getKey().ownerType(),
                    entry.getKey().resourceId(),
                    entry.getKey().resourceType(),
                    entry.getValue()))
        .toList();
  }
}
