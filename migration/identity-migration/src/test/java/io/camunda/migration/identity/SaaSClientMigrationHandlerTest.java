/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.assertArg;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.migration.identity.console.ConsoleClient;
import io.camunda.migration.identity.dto.Client;
import io.camunda.migration.identity.dto.MigrationStatusUpdateRequest;
import io.camunda.migration.identity.midentity.ManagementIdentityClient;
import io.camunda.migration.identity.midentity.MigrationEntityType;
import io.camunda.service.AuthorizationServices;
import io.camunda.service.MappingServices;
import io.camunda.service.MappingServices.MappingDTO;
import io.camunda.zeebe.broker.client.api.BrokerRejectionException;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.lang3.NotImplementedException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class SaaSClientMigrationHandlerTest {
  private final SaaSClientMigrationHandler migrationHandler;
  private final ConsoleClient consoleClient;
  private final ManagementIdentityClient managementIdentityClient;
  private final AuthorizationServices authorizationServices;
  private final MappingServices mappingServices;

  public SaaSClientMigrationHandlerTest(
      @Mock final ConsoleClient consoleClient,
      @Mock final ManagementIdentityClient managementIdentityClient,
      @Mock final AuthorizationServices authorizationServices,
      @Mock final MappingServices mappingServices) {
    this.consoleClient = consoleClient;
    this.managementIdentityClient = managementIdentityClient;
    this.authorizationServices = authorizationServices;
    this.mappingServices = mappingServices;
    migrationHandler =
        new SaaSClientMigrationHandler(
            consoleClient, managementIdentityClient, mappingServices, authorizationServices);
    when(mappingServices.createMapping(any()))
        .thenReturn(CompletableFuture.completedFuture(new MappingRecord()));
  }

  @Test
  void stopWhenIdentityEndpointNotFound() {
    when(managementIdentityClient.fetchMigrationStatus(MigrationEntityType.CLIENT, true))
        .thenThrow(new NotImplementedException());

    // when
    assertThrows(NotImplementedException.class, migrationHandler::migrate);

    // then
    verify(managementIdentityClient).fetchMigrationStatus(MigrationEntityType.CLIENT, true);
    verifyNoMoreInteractions(managementIdentityClient);
  }

  @Test
  void stopWhenNoMoreRecords() {
    // given
    givenClients();

    // when
    migrationHandler.migrate();

    // then
    verify(managementIdentityClient, times(2))
        .fetchMigrationStatus(MigrationEntityType.CLIENT, true);
    verify(consoleClient, times(2)).fetchClients();
    verify(mappingServices, times(2)).createMapping(any());
    verify(authorizationServices, times(2)).patchAuthorization(any());
  }

  @Test
  void setErrorWhenClientCreationFailed() {
    // given
    givenClients();
    when(mappingServices.createMapping(any(MappingDTO.class))).thenThrow(new RuntimeException());

    // when
    migrationHandler.migrate();

    // then
    verify(managementIdentityClient, times(2))
        .fetchMigrationStatus(MigrationEntityType.CLIENT, true);
    verify(consoleClient, times(2)).fetchClients();
    verify(mappingServices, times(2)).findMapping(any(MappingDTO.class));
    verify(managementIdentityClient, times(2))
        .updateMigrationStatus(
            assertArg(
                migrationStatusUpdateRequests -> {
                  Assertions.assertThat(migrationStatusUpdateRequests)
                      .describedAs("All migrations have failed")
                      .noneMatch(MigrationStatusUpdateRequest::success);
                }));
  }

  @Test
  void ignoreWhenAuthorizationAlreadyAssigned() {
    // given
    givenClients();
    doThrow(
            new BrokerRejectionException(
                new BrokerRejection(TenantIntent.ADD_ENTITY, -1, RejectionType.ALREADY_EXISTS, "")))
        .when(authorizationServices)
        .patchAuthorization(any());

    // when
    migrationHandler.migrate();

    // then
    verify(managementIdentityClient, times(2))
        .fetchMigrationStatus(MigrationEntityType.CLIENT, true);
    verify(consoleClient, times(2)).fetchClients();
    verify(mappingServices, times(2)).findMapping(any(MappingDTO.class));
    verify(managementIdentityClient, times(2))
        .updateMigrationStatus(
            assertArg(
                migrationStatusUpdateRequests -> {
                  Assertions.assertThat(migrationStatusUpdateRequests)
                      .describedAs("All migrations are successful")
                      .allMatch(MigrationStatusUpdateRequest::success);
                }));
  }

  private void givenClients() {
    when(consoleClient.fetchClients())
        .thenReturn(
            List.of(
                new Client("cn1", "c1", List.of("operate", "identity")),
                new Client("cn2", "c2", List.of("tasklist"))))
        .thenReturn(Collections.emptyList());
  }
}
