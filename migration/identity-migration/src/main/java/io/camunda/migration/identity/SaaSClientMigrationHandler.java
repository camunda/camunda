/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity;

import static io.camunda.migration.identity.midentity.ManagementIdentityTransformer.toMigrationStatusUpdateRequest;
import static io.camunda.migration.identity.transformer.AuthorizationTransformer.transformApplicationAccess;

import io.camunda.migration.identity.console.ConsoleClient;
import io.camunda.migration.identity.dto.Client;
import io.camunda.migration.identity.dto.MigrationStatusRecord;
import io.camunda.migration.identity.dto.MigrationStatusUpdateRequest;
import io.camunda.migration.identity.midentity.ManagementIdentityClient;
import io.camunda.migration.identity.midentity.MigrationEntityType;
import io.camunda.search.entities.MappingEntity;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.MappingServices;
import io.camunda.service.MappingServices.MappingDTO;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnBean(ConsoleClient.class)
public class SaaSClientMigrationHandler extends ClientMigrationHandler {

  private static final String CLIENT_CLAIM = "aud";
  private static final Logger LOG = LoggerFactory.getLogger(SaaSClientMigrationHandler.class);
  private final MappingServices mappingServices;
  private final AuthorizationServices authorizationServices;
  private final ConsoleClient consoleClient;
  private final ManagementIdentityClient managementIdentityClient;

  public SaaSClientMigrationHandler(
      final ConsoleClient consoleClient,
      final ManagementIdentityClient managementIdentityClient,
      final MappingServices mappingServices,
      final AuthorizationServices authorizationServices) {
    this.consoleClient = consoleClient;
    this.managementIdentityClient = managementIdentityClient;
    this.mappingServices = mappingServices;
    this.authorizationServices = authorizationServices;
  }

  @Override
  protected List<Client> fetchBatch() {
    final List<String> migratedClients =
        managementIdentityClient
            .fetchMigrationStatus(MigrationEntityType.CLIENT, Boolean.TRUE)
            .stream()
            .map(MigrationStatusRecord::entityId)
            .toList();

    return consoleClient.fetchClients().stream()
        .dropWhile(client -> migratedClients.contains(client.clientId()))
        .toList();
  }

  @Override
  protected void process(final List<Client> batch) {
    managementIdentityClient.updateMigrationStatus(batch.stream().map(this::createClient).toList());
  }

  private MigrationStatusUpdateRequest createClient(final Client client) {

    final long mappingKey;
    try {
      final var mapping =
          new MappingDTO(CLIENT_CLAIM, client.clientId(), client.clientId() + "_mapping");

      mappingKey =
          mappingServices
              .findMapping(mapping)
              .map(MappingEntity::mappingKey)
              .orElseGet(() -> mappingServices.createMapping(mapping).join().getMappingKey());
    } catch (final Exception e) {
      LOG.error("mapping key for client failed", e);
      return toMigrationStatusUpdateRequest(client, e);
    }

    try {
      transformApplicationAccess(mappingKey, client.permissions()).stream()
          .map(authorizationServices::patchAuthorization)
          .forEach(CompletableFuture::join);
    } catch (final Exception e) {
      LOG.error("patch authorization for role failed", e);
      if (!isConflictError(e)) {
        return toMigrationStatusUpdateRequest(client, e);
      }
    }
    return toMigrationStatusUpdateRequest(client, null);
  }
}
