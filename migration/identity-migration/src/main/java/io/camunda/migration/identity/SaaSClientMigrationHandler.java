/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity;

import static io.camunda.migration.identity.transformer.AuthorizationTransformer.transformApplicationAccess;

import io.camunda.migration.identity.console.ConsoleClient;
import io.camunda.migration.identity.dto.Client;
import io.camunda.migration.identity.dto.MigrationStatusUpdateRequest;
import io.camunda.migration.identity.midentity.ManagementIdentityClient;
import io.camunda.migration.identity.midentity.ManagementIdentityTransformer;
import io.camunda.search.entities.MappingEntity;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.MappingServices;
import io.camunda.service.MappingServices.MappingDTO;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SaaSClientMigrationHandler implements MigrationHandler {

  private static final String CLIENT_CLAIM = "aud";
  private static final Logger LOG = LoggerFactory.getLogger(SMRoleMigrationHandler.class);
  private final MappingServices mappingServices;
  private final AuthorizationServices authorizationServices;
  private final ConsoleClient consoleClient;
  private final ManagementIdentityClient managementIdentityClient;
  private final ManagementIdentityTransformer managementIdentityTransformer;

  public SaaSClientMigrationHandler(
      final ConsoleClient consoleClient,
      final ManagementIdentityClient managementIdentityClient,
      final MappingServices mappingServices,
      final AuthorizationServices authorizationServices,
      final ManagementIdentityTransformer managementIdentityTransformer) {
    this.consoleClient = consoleClient;
    this.managementIdentityClient = managementIdentityClient;
    this.mappingServices = mappingServices;
    this.authorizationServices = authorizationServices;
    this.managementIdentityTransformer = managementIdentityTransformer;
  }

  @Override
  public void migrate() {
    LOG.debug("Migrating clients");
    List<Client> clients;
    do {
      clients = consoleClient.fetchClients();
      managementIdentityClient.updateMigrationStatus(
          clients.stream().map(this::createClient).toList());
    } while (!clients.isEmpty());
  }

  private MigrationStatusUpdateRequest createClient(final Client client) {

    final var mapping =
        new MappingDTO(CLIENT_CLAIM, client.clientId(), client.clientId() + "_mapping");

    final var mappingKey =
        mappingServices
            .findMapping(mapping)
            .map(MappingEntity::mappingKey)
            .orElseGet(() -> mappingServices.createMapping(mapping).join().getMappingKey());

    try {
      transformApplicationAccess(mappingKey, client.permissions()).stream()
          .map(authorizationServices::patchAuthorization)
          .forEach(CompletableFuture::join);
    } catch (final Exception e) {
      LOG.error("patch authorization for role failed", e);
      if (!isConflictError(e)) {
        return managementIdentityTransformer.toMigrationStatusUpdateRequest(client, e);
      }
    }
    return managementIdentityTransformer.toMigrationStatusUpdateRequest(client, null);
  }
}
