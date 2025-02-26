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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.assertArg;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.migration.identity.dto.MigrationStatusUpdateRequest;
import io.camunda.migration.identity.dto.Tenant;
import io.camunda.migration.identity.dto.UserTenants;
import io.camunda.migration.identity.midentity.ManagementIdentityClient;
import io.camunda.migration.identity.midentity.ManagementIdentityTransformer;
import io.camunda.search.entities.TenantEntity;
import io.camunda.service.MappingServices;
import io.camunda.service.MappingServices.MappingDTO;
import io.camunda.service.TenantServices;
import io.camunda.zeebe.broker.client.api.BrokerRejectionException;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRecord;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.lang3.NotImplementedException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class UserTenantsMigrationHandlerTest {
  private final ManagementIdentityClient managementIdentityClient;
  private final TenantServices tenantServices;
  private final MappingServices mappingServices;

  private final UserTenantsMigrationHandler migrationHandler;

  public UserTenantsMigrationHandlerTest(
      @Mock final ManagementIdentityClient managementIdentityClient,
      @Mock(answer = Answers.RETURNS_SELF) final TenantServices tenantServices,
      @Mock(answer = Answers.RETURNS_SELF) final MappingServices mappingServices) {
    when(tenantServices.createTenant(any()))
        .thenReturn(CompletableFuture.completedFuture(new TenantRecord()));
    when(tenantServices.addMember(anyString(), any(), anyLong()))
        .thenReturn(CompletableFuture.completedFuture(new TenantRecord()));
    when(mappingServices.createMapping(any()))
        .thenReturn(CompletableFuture.completedFuture(new MappingRecord()));

    this.managementIdentityClient = managementIdentityClient;
    this.tenantServices = tenantServices;
    this.mappingServices = mappingServices;
    migrationHandler =
        new UserTenantsMigrationHandler(
            managementIdentityClient,
            new ManagementIdentityTransformer(),
            tenantServices,
            mappingServices);
  }

  @Test
  void stopWhenIdentityEndpointNotFound() {
    when(managementIdentityClient.fetchUserTenants(anyInt()))
        .thenThrow(new NotImplementedException());

    // when
    assertThrows(NotImplementedException.class, migrationHandler::migrate);

    // then
    verify(managementIdentityClient).fetchUserTenants(anyInt());
    verifyNoMoreInteractions(managementIdentityClient);
  }

  @Test
  void stopWhenNoMoreRecords() {
    // given
    givenUserTenants();
    when(tenantServices.getById(any())).thenReturn(new TenantEntity(1L, "", "", null));
    when(tenantServices.createTenant(any()))
        .thenReturn(CompletableFuture.completedFuture(new TenantRecord()));
    when(mappingServices.createMapping(any()))
        .thenReturn(CompletableFuture.completedFuture(new MappingRecord()));
    when(tenantServices.addMember(anyString(), any(), anyLong()))
        .thenReturn(CompletableFuture.completedFuture(new TenantRecord()));

    // when
    migrationHandler.migrate();

    // then
    verify(managementIdentityClient, times(2)).fetchUserTenants(anyInt());
    verify(tenantServices, times(4)).getById(any());
    verify(tenantServices, times(4)).addMember(anyString(), any(), anyLong());
    verify(mappingServices, times(2)).findMapping(any(MappingDTO.class));
  }

  @Test
  void setErrorWhenUserCreationFailed() {
    // given
    givenUserTenants();
    when(mappingServices.createMapping(any(MappingDTO.class))).thenThrow(new RuntimeException());

    // when
    migrationHandler.migrate();

    // then
    verify(managementIdentityClient, times(2)).fetchUserTenants(anyInt());
    verify(tenantServices, never()).getById(any());
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
  void setErrorWhenTenantNotFound() {
    // given
    givenUserTenants();
    when(tenantServices.getById(any())).thenThrow(new RuntimeException());

    // when
    migrationHandler.migrate();

    // then
    verify(managementIdentityClient, times(2)).fetchUserTenants(anyInt());
    verify(tenantServices, times(2)).getById(any());
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
  void ignoreWhenUserAlreadyAssigned() {
    // given
    givenUserTenants();
    when(tenantServices.getById(any())).thenReturn(new TenantEntity(1L, "", "", null));
    doThrow(
            new BrokerRejectionException(
                new BrokerRejection(TenantIntent.ADD_ENTITY, -1, RejectionType.ALREADY_EXISTS, "")))
        .when(tenantServices)
        .addMember(anyString(), any(), anyLong());

    // when
    migrationHandler.migrate();

    // then
    verify(managementIdentityClient, times(2)).fetchUserTenants(anyInt());
    verify(tenantServices, times(4)).getById(any());
    verify(tenantServices, times(4)).addMember(anyString(), any(), anyLong());
    verify(mappingServices, times(2)).createMapping(any());
    verify(managementIdentityClient, times(2))
        .updateMigrationStatus(
            assertArg(
                migrationStatusUpdateRequests ->
                    Assertions.assertThat(migrationStatusUpdateRequests)
                        .describedAs("All migrations have succeeded")
                        .allMatch(MigrationStatusUpdateRequest::success)));
  }

  private void givenUserTenants() {
    when(managementIdentityClient.fetchUserTenants(anyInt()))
        .thenReturn(
            List.of(
                new UserTenants(
                    UUID.randomUUID().toString(),
                    "user1",
                    List.of(new Tenant("id1", "t1"), new Tenant("id2", "t2"))),
                new UserTenants(
                    UUID.randomUUID().toString(),
                    "user2",
                    List.of(new Tenant("id1", "t1"), new Tenant("id3", "t3")))))
        .thenReturn(List.of());
  }
}
