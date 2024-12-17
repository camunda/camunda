/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.assertArg;
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
import io.camunda.search.entities.TenantEntity;
import io.camunda.service.TenantServices;
import io.camunda.zeebe.client.api.command.ProblemException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class TenantMappingRuleMigrationHandlerTest {
  private final ManagementIdentityClient managementIdentityClient;
  private final TenantServices tenantServices;
  private final MappingService mappingService;

  private final TenantMappingRuleMigrationHandler migrationHandler;

  public TenantMappingRuleMigrationHandlerTest(
      @Mock final ManagementIdentityClient managementIdentityClient,
      @Mock(answer = Answers.RETURNS_SELF) final TenantServices tenantServices,
      @Mock final MappingService mappingService) {
    this.managementIdentityClient = managementIdentityClient;
    this.tenantServices = tenantServices;
    this.mappingService = mappingService;
    migrationHandler =
        new TenantMappingRuleMigrationHandler(
            managementIdentityClient,
            new ManagementIdentityTransformer(),
            tenantServices,
            mappingService);
  }

  @Test
  void stopWhenNoMoreRecords() {
    // given
    givenMappingRules();
    when(tenantServices.getById(any()))
        .thenReturn(new TenantEntity(1L, "", "", Collections.emptySet()));

    // when
    migrationHandler.migrate();

    // then
    verify(managementIdentityClient, times(2)).fetchTenantMappingRules(anyInt());
    verify(tenantServices, times(4)).getById(any());
    verify(tenantServices, times(4)).addMember(any(), any(), anyLong());
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
    verify(tenantServices, never()).search(any());
    verify(mappingService, times(2)).findOrCreateMapping(any(), any(), any());
    verify(managementIdentityClient, times(2))
        .updateMigrationStatus(
            assertArg(
                migrationStatusUpdateRequests -> {
                  assertThat(migrationStatusUpdateRequests)
                      .describedAs("All requests should fail")
                      .noneMatch(MigrationStatusUpdateRequest::success);
                }));
  }

  @Test
  void setErrorWhenTenantNotFound() {
    // given
    givenMappingRules();
    when(tenantServices.getById(any()))
        .thenThrow(new ProblemException(0, "runtime exception!", null));

    // when
    migrationHandler.migrate();

    // then
    verify(managementIdentityClient, times(2)).fetchTenantMappingRules(anyInt());
    verify(tenantServices, times(2)).getById(any());
    verify(mappingService, times(2)).findOrCreateMapping(any(), any(), any());
    verify(managementIdentityClient, times(2))
        .updateMigrationStatus(
            assertArg(
                migrationStatusUpdateRequests -> {
                  assertThat(migrationStatusUpdateRequests)
                      .describedAs("All requests should fail")
                      .noneMatch(MigrationStatusUpdateRequest::success);
                }));
  }

  @Test
  void ignoreWhenMappingAlreadyAssigned() {
    // given
    givenMappingRules();
    when(tenantServices.getById(any()))
        .thenReturn(new TenantEntity(1L, "", "", Collections.emptySet()));
    doThrow(new RuntimeException("Failed with code 409: 'Conflict'"))
        .when(tenantServices)
        .addMember(any(), any(), anyLong());

    // when
    migrationHandler.migrate();

    // then
    verify(managementIdentityClient, times(2)).fetchTenantMappingRules(anyInt());
    verify(tenantServices, times(4)).getById(any());
    verify(tenantServices, times(4)).addMember(any(), any(), anyLong());
    verify(mappingService, times(2)).findOrCreateMapping(any(), any(), any());
    verify(managementIdentityClient, times(2))
        .updateMigrationStatus(
            assertArg(
                migrationStatusUpdateRequests -> {
                  assertThat(migrationStatusUpdateRequests)
                      .describedAs("All requests should succeed")
                      .allMatch(MigrationStatusUpdateRequest::success);
                }));
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
