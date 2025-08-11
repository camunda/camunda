/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.handler.sm;

import static io.camunda.migration.identity.MigrationUtil.extractCombinedPermissions;
import static io.camunda.migration.identity.config.sm.StaticEntities.getAuthorizationsByAudience;

import io.camunda.migration.api.MigrationException;
import io.camunda.migration.identity.client.ManagementIdentityClient;
import io.camunda.migration.identity.config.IdentityMigrationProperties;
import io.camunda.migration.identity.dto.Client;
import io.camunda.migration.identity.handler.MigrationHandler;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.AuthorizationServices;
import io.camunda.zeebe.protocol.record.value.AuthorizationOwnerType;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientMigrationHandler extends MigrationHandler<Client> {

  private final ManagementIdentityClient managementIdentityClient;
  private final AuthorizationServices authorizationService;
  private final IdentityMigrationProperties migrationProperties;

  private final AtomicInteger createdAuthorizationsCount = new AtomicInteger();
  private final AtomicInteger skippedAuthorizationsCount = new AtomicInteger();
  private final AtomicInteger totalAuthorizationsCount = new AtomicInteger();

  public ClientMigrationHandler(
      final CamundaAuthentication authentication,
      final ManagementIdentityClient managementIdentityClient,
      final AuthorizationServices authorizationService,
      final IdentityMigrationProperties migrationProperties) {
    super(migrationProperties.getBackpressureDelay());
    this.managementIdentityClient = managementIdentityClient;
    this.authorizationService = authorizationService.withAuthentication(authentication);
    this.migrationProperties = migrationProperties;
  }

  @Override
  protected List<Client> fetchBatch(final int page) {
    return List.of();
  }

  @Override
  protected void process(final List<Client> batch) {
    final var clients = managementIdentityClient.fetchClients();

    clients.stream()
        .filter(
            client -> {
              switch (client.type()) {
                case M2M, CONFIDENTIAL -> {
                  return true;
                }
                default -> {
                  logger.debug(
                      "Got client with type {} which is not relevant for migration, skipping.",
                      client.type());
                  return false;
                }
              }
            })
        .forEach(
            client -> {
              final var clientId = client.clientId();
              final var permissions =
                  managementIdentityClient.fetchClientPermissions(client.id()).stream()
                      .map(
                          permission ->
                              String.format(
                                  "%s:%s",
                                  permission.resourceServerAudience(), permission.definition()))
                      .toList();
              if (permissions.isEmpty()) {
                logger.debug(
                    "No permissions found for client '{}', skipping authorization creation.",
                    clientId);
                return;
              }
              totalAuthorizationsCount.incrementAndGet();

              final var authorizations =
                  permissions.stream()
                      .map(
                          permission ->
                              getAuthorizationsByAudience(
                                  migrationProperties.getOidc().getAudience(),
                                  permission,
                                  clientId,
                                  AuthorizationOwnerType.CLIENT))
                      .flatMap(Set::stream)
                      .toList();

              final var combinedPermissions = extractCombinedPermissions(authorizations);

              for (final var request : combinedPermissions) {
                try {
                  retryOnBackpressure(
                      () -> authorizationService.createAuthorization(request).join(),
                      "Failed to create authorization for client '"
                          + clientId
                          + "' with permissions '"
                          + request.permissionTypes()
                          + "'");
                  logger.debug(
                      "Authorization created for client '{}' with permissions '{}'.",
                      clientId,
                      request.permissionTypes());
                } catch (final Exception e) {
                  if (!isConflictError(e)) {
                    throw new MigrationException(
                        String.format(
                            "Failed to create authorization for client '%s' with permissions '%s'",
                            clientId, request.permissionTypes()),
                        e);
                  }
                  logger.debug(
                      "Authorization already exists for client '{}' with permissions '{}', skipping.",
                      clientId,
                      request.permissionTypes());
                }
              }
            });
  }

  @Override
  protected void logSummary() {
    logger.info(
        "Client authorization migration complete: Created {} out of {} authorizations. Skipped {} as they already exist.",
        createdAuthorizationsCount.get(),
        totalAuthorizationsCount.get(),
        skippedAuthorizationsCount.get());
  }
}
