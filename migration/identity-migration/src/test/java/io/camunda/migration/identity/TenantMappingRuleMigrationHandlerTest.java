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

import io.camunda.migration.identity.dto.MappingRule.Operator;
import io.camunda.migration.identity.dto.MigrationStatusUpdateRequest;
import io.camunda.migration.identity.dto.Tenant;
import io.camunda.migration.identity.dto.TenantMappingRule;
import io.camunda.migration.identity.midentity.ManagementIdentityClient;
import io.camunda.migration.identity.midentity.ManagementIdentityTransformer;
import io.camunda.migration.identity.service.MappingService;
import io.camunda.migration.identity.service.TenantService;
import io.camunda.search.entities.TenantEntity;
import io.camunda.zeebe.client.api.command.ProblemException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class TenantMappingRuleMigrationHandlerTest {
  final ArgumentCaptor<Collection<MigrationStatusUpdateRequest>> migrationStatusCaptor =
      ArgumentCaptor.forClass(Collection.class);
  @Mock private ManagementIdentityClient managementIdentityClient;
  @Mock private TenantService tenantService;
  @Mock private MappingService mappingService;

  private final ManagementIdentityTransformer managementIdentityTransformer =
      new ManagementIdentityTransformer();
  private TenantMappingRuleMigrationHandler migrationHandler;

  @BeforeEach
  void setUp() throws Exception {
    MockitoAnnotations.openMocks(this).close();
    // when(tenantService.fetchOrCreateTenant(any(), any())).thenReturn(null);
    migrationHandler =
        new TenantMappingRuleMigrationHandler(
            managementIdentityClient, managementIdentityTransformer, tenantService, mappingService);
  }

  @AfterEach
  void tearDown() {}

  @Test
  void stopWhenNoMoreRecords() {
    // given
    givenMappingRules();
    when(tenantService.fetch(any(), any()))
        .thenReturn(new TenantEntity(1L, "", "", Collections.emptySet()));

    // when
    migrationHandler.migrate();

    // then
    verify(managementIdentityClient, times(2)).fetchTenantMappingRules(anyInt());
    verify(tenantService, times(4)).fetch(any(), any());
    verify(tenantService, times(4)).assignMappingToTenant(any(), any());
    verify(mappingService, times(2)).findOrCreateMapping(any(), any(), any());
  }

  @Test
  void setErrorWhenMappingRuleCreationFailed() {
    // given
    givenMappingRules();
    when(mappingService.findOrCreateMapping(any(), any(), any())).thenThrow(new RuntimeException());

    // when
    migrationHandler.migrate();

    // then
    verify(managementIdentityClient, times(2)).fetchTenantMappingRules(anyInt());
    verify(tenantService, never()).fetch(any(), any());
    verify(mappingService, times(2)).findOrCreateMapping(any(), any(), any());
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
    givenMappingRules();
    when(tenantService.fetch(any(), any()))
        .thenThrow(new ProblemException(0, "runtime exception!", null));

    // when
    migrationHandler.migrate();

    // then
    verify(managementIdentityClient, times(2)).fetchTenantMappingRules(anyInt());
    verify(tenantService, times(2)).fetch(any(), any());
    verify(mappingService, times(2)).findOrCreateMapping(any(), any(), any());
    verify(managementIdentityClient, times(2))
        .updateMigrationStatus(migrationStatusCaptor.capture());
    assertTrue(
        migrationStatusCaptor.getAllValues().stream()
            .flatMap(Collection::stream)
            .noneMatch(MigrationStatusUpdateRequest::success),
        "All requests should failed");
  }

  @Test
  void ignoreWhenMappingAlreadyAssigned() {
    // given
    givenMappingRules();
    when(tenantService.fetch(any(), any()))
        .thenReturn(new TenantEntity(1L, "", "", Collections.emptySet()));
    doThrow(new RuntimeException("Failed with code 409: 'Conflict'"))
        .when(tenantService)
        .assignMappingToTenant(any(), any());

    // when
    migrationHandler.migrate();

    // then
    verify(managementIdentityClient, times(2)).fetchTenantMappingRules(anyInt());
    verify(tenantService, times(4)).fetch(any(), any());
    verify(tenantService, times(4)).assignMappingToTenant(any(), any());
    verify(mappingService, times(2)).findOrCreateMapping(any(), any(), any());
    verify(managementIdentityClient, times(2))
        .updateMigrationStatus(migrationStatusCaptor.capture());
    assertTrue(
        migrationStatusCaptor.getAllValues().stream()
            .flatMap(Collection::stream)
            .allMatch(MigrationStatusUpdateRequest::success),
        "All requests should succeed");
  }

  private void givenMappingRules() {
    when(managementIdentityClient.fetchTenantMappingRules(anyInt()))
        .thenReturn(
            List.of(
                new TenantMappingRule(
                    "tr1",
                    "c",
                    "v1",
                    Operator.CONTAINS,
                    Set.of(new Tenant("id1", "t1"), new Tenant("id2", "t2"))),
                new TenantMappingRule(
                    "tr2",
                    "c",
                    "v2",
                    Operator.CONTAINS,
                    Set.of(new Tenant("id1", "t1"), new Tenant("id3", "t3")))))
        .thenReturn(List.of());
  }
}
