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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.migration.identity.dto.MigrationStatusUpdateRequest;
import io.camunda.migration.identity.dto.Tenant;
import io.camunda.migration.identity.midentity.ManagementIdentityClient;
import io.camunda.migration.identity.midentity.ManagementIdentityTransformer;
import io.camunda.migration.identity.service.TenantService;
import io.camunda.zeebe.client.api.command.ProblemException;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TenantMigrationHandlerTest {
  final ArgumentCaptor<Collection<MigrationStatusUpdateRequest>> migrationStatusCaptor =
      ArgumentCaptor.forClass(Collection.class);
  @Mock private ManagementIdentityClient managementIdentityClient;
  @Mock private TenantService tenantService;
  private final ManagementIdentityTransformer managementIdentityTransformer =
      new ManagementIdentityTransformer();
  private TenantMigrationHandler migrationHandler;

  @BeforeEach
  void setUp() throws Exception {
    MockitoAnnotations.openMocks(this).close();
    migrationHandler =
        new TenantMigrationHandler(
            managementIdentityClient, managementIdentityTransformer, tenantService);
  }

  @AfterEach
  void tearDown() {}

  @Test
  void stopWhenNoMoreRecords() {
    // given
    when(managementIdentityClient.fetchTenants(anyInt()))
        .thenReturn(List.of(new Tenant("id1", "t1"), new Tenant("id2", "t2")))
        .thenReturn(List.of());

    // when
    migrationHandler.migrate();

    // then
    verify(managementIdentityClient, times(2)).fetchTenants(anyInt());
    verify(tenantService, times(2)).create(any(), any());
  }

  @Test
  void ignoreWhenTenantAlreadyExists() {
    // given
    when(tenantService.create(any(), any()))
        .thenThrow(new ProblemException(0, "Failed with code 409: 'Conflict'", null));
    when(managementIdentityClient.fetchTenants(anyInt()))
        .thenReturn(List.of(new Tenant("id1", "t1"), new Tenant("id2", "t2")))
        .thenReturn(List.of());

    // when
    migrationHandler.migrate();

    // then
    verify(managementIdentityClient, times(2)).fetchTenants(anyInt());
    verify(tenantService, times(2)).create(any(), any());
    verify(managementIdentityClient, times(2))
        .updateMigrationStatus(migrationStatusCaptor.capture());
    assertTrue(
        migrationStatusCaptor.getAllValues().stream()
            .flatMap(Collection::stream)
            .allMatch(MigrationStatusUpdateRequest::success),
        "All requests should succeed");
  }

  @Test
  void setErrorWhenTenantCreationHasError() {
    // given
    when(tenantService.create(any(), any()))
        .thenThrow(new ProblemException(0, "runtime exception!", null));
    when(managementIdentityClient.fetchTenants(anyInt()))
        .thenReturn(List.of(new Tenant("id1", "t1"), new Tenant("id2", "t2")))
        .thenReturn(List.of());

    // when
    migrationHandler.migrate();

    // then
    verify(managementIdentityClient, times(2))
        .updateMigrationStatus(migrationStatusCaptor.capture());
    assertTrue(
        migrationStatusCaptor.getAllValues().stream()
            .flatMap(Collection::stream)
            .noneMatch(MigrationStatusUpdateRequest::success),
        "All requests should failed");
    verify(managementIdentityClient, times(2)).fetchTenants(anyInt());
    verify(tenantService, times(2)).create(any(), any());
  }
}
