/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.migration.identity.dto.MigrationStatusUpdateRequest;
import io.camunda.migration.identity.dto.Tenant;
import io.camunda.migration.identity.dto.UserTenants;
import io.camunda.migration.identity.midentity.ManagementIdentityClient;
import io.camunda.migration.identity.midentity.ManagementIdentityTransformer;
import io.camunda.migration.identity.service.MappingService;
import io.camunda.migration.identity.service.TenantService;
import io.camunda.search.entities.TenantEntity;
import io.camunda.zeebe.client.api.command.ProblemException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class UserTenantsMigrationHandlerTest {
  final ArgumentCaptor<Collection<MigrationStatusUpdateRequest>> migrationStatusCaptor =
      ArgumentCaptor.forClass(Collection.class);

  @Mock private ManagementIdentityClient managementIdentityClient;
  @Mock private TenantService tenantService;
  @Mock private MappingService mappingService;

  private final ManagementIdentityTransformer managementIdentityTransformer =
      new ManagementIdentityTransformer();
  private UserTenantsMigrationHandler migrationHandler;

  @BeforeEach
  void setUp() throws Exception {
    MockitoAnnotations.openMocks(this).close();
    migrationHandler =
        new UserTenantsMigrationHandler(
            managementIdentityClient, managementIdentityTransformer, tenantService, mappingService);
  }

  @AfterEach
  void tearDown() {}

  @Test
  void stopWhenNoMoreRecords() {
    // given
    givenUserTenants();
    when(tenantService.fetch(any(), any()))
        .thenReturn(new TenantEntity(1L, "", "", Collections.emptySet()));

    // when
    migrationHandler.migrate();

    // then
    verify(managementIdentityClient, times(2)).fetchUserTenants(anyInt());
    verify(tenantService, times(4)).fetch(any(), any());
    verify(tenantService, times(4)).assignMappingToTenant(any(), any());
    verify(mappingService, times(2)).findOrCreateUserWithUsername(any());
  }

  @Test
  void setErrorWhenUserCreationFailed() {
    // given
    givenUserTenants();
    when(mappingService.findOrCreateUserWithUsername(any())).thenThrow(new RuntimeException());

    // when
    migrationHandler.migrate();

    // then
    verify(managementIdentityClient, times(2)).fetchUserTenants(anyInt());
    verify(tenantService, never()).fetch(any(), any());
    verify(mappingService, times(2)).findOrCreateUserWithUsername(any());
    verify(managementIdentityClient, times(2))
        .updateMigrationStatus(migrationStatusCaptor.capture());
    assertTrue(
        migrationStatusCaptor.getAllValues().stream()
            .flatMap(Collection::stream)
            .noneMatch(MigrationStatusUpdateRequest::success),
        "All requests should failed");
  }

  @Test
  void setErrorWhenTenantNotFound() {
    // given
    givenUserTenants();
    when(tenantService.fetch(any(), any()))
        .thenThrow(new ProblemException(0, "runtime exception!", null));

    // when
    migrationHandler.migrate();

    // then
    verify(managementIdentityClient, times(2)).fetchUserTenants(anyInt());
    verify(tenantService, times(2)).fetch(any(), any());
    verify(mappingService, times(2)).findOrCreateUserWithUsername(any());
    verify(managementIdentityClient, times(2))
        .updateMigrationStatus(migrationStatusCaptor.capture());
    assertTrue(
        migrationStatusCaptor.getAllValues().stream()
            .flatMap(Collection::stream)
            .noneMatch(MigrationStatusUpdateRequest::success),
        "All requests should failed");
  }

  @Test
  void ignoreWhenUserAlreadyAssigned() {
    // given
    givenUserTenants();
    when(tenantService.fetch(any(), any()))
        .thenReturn(new TenantEntity(1L, "", "", Collections.emptySet()));
    doThrow(new RuntimeException("Failed with code 409: 'Conflict'"))
        .when(tenantService)
        .assignMappingToTenant(any(), any());

    // when
    migrationHandler.migrate();

    // then
    verify(managementIdentityClient, times(2)).fetchUserTenants(anyInt());
    verify(tenantService, times(4)).fetch(any(), any());
    verify(tenantService, times(4)).assignMappingToTenant(any(), any());
    verify(mappingService, times(2)).findOrCreateUserWithUsername(any());
    verify(managementIdentityClient, times(2))
        .updateMigrationStatus(migrationStatusCaptor.capture());
    assertTrue(
        migrationStatusCaptor.getAllValues().stream()
            .flatMap(Collection::stream)
            .allMatch(MigrationStatusUpdateRequest::success),
        "All requests should succeed");
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
