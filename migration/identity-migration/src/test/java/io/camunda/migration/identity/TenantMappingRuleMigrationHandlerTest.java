/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.assertArg;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.migration.identity.dto.MappingRule.Operator;
import io.camunda.migration.identity.dto.MigrationStatusUpdateRequest;
import io.camunda.migration.identity.dto.Tenant;
import io.camunda.migration.identity.dto.TenantMappingRule;
import io.camunda.migration.identity.midentity.ManagementIdentityClient;
import io.camunda.migration.identity.midentity.ManagementIdentityTransformer;
import io.camunda.search.entities.TenantEntity;
import io.camunda.security.auth.Authentication;
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
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class TenantMappingRuleMigrationHandlerTest {
  private final ManagementIdentityClient managementIdentityClient;
  private final TenantServices tenantServices;
  private final MappingServices mappingServices;

  private final TenantMappingRuleMigrationHandler migrationHandler;

  public TenantMappingRuleMigrationHandlerTest(
      @Mock final ManagementIdentityClient managementIdentityClient,
      @Mock(answer = Answers.RETURNS_SELF) final TenantServices tenantServices,
      @Mock(answer = Answers.RETURNS_SELF) final MappingServices mappingServices) {
    this.managementIdentityClient = managementIdentityClient;
    this.tenantServices = tenantServices;
    this.mappingServices = mappingServices;
    migrationHandler =
        new TenantMappingRuleMigrationHandler(
            Authentication.none(),
            managementIdentityClient,
            new ManagementIdentityTransformer(),
            tenantServices,
            mappingServices);
  }

  @Test
  void stopWhenIdentityEndpointNotFound() {
    when(managementIdentityClient.fetchTenantMappingRules(anyInt()))
        .thenThrow(new NotImplementedException());

    // when
    assertThrows(NotImplementedException.class, migrationHandler::migrate);

    // then
    verify(managementIdentityClient).fetchTenantMappingRules(anyInt());
    verifyNoMoreInteractions(managementIdentityClient);
  }

  @Test
  void stopWhenNoMoreRecords() {
    // given
    givenMappingRules();
    when(tenantServices.getById(any())).thenReturn(new TenantEntity(1L, "", "", null));
    when(mappingServices.createMapping(any()))
        .thenAnswer(invocation -> CompletableFuture.completedFuture(new MappingRecord()));
    when(tenantServices.addMember(anyString(), any(), anyString()))
        .thenReturn(CompletableFuture.completedFuture(new TenantRecord()));
    // when
    migrationHandler.migrate();

    // then
    verify(managementIdentityClient, times(2)).fetchTenantMappingRules(anyInt());
    verify(tenantServices, times(4)).getById(any());
    verify(tenantServices, times(4)).addMember(anyString(), any(), anyString());
    verify(mappingServices, times(2)).findMapping(any(MappingDTO.class));
  }

  @Test
  void setErrorWhenMappingRuleCreationFailed() {
    // given
    givenMappingRules();
    when(mappingServices.findMapping(any(MappingDTO.class))).thenThrow(new RuntimeException());

    // when
    migrationHandler.migrate();

    // then
    verify(managementIdentityClient, times(2)).fetchTenantMappingRules(anyInt());
    verify(tenantServices, never()).search(any());
    verify(mappingServices, times(2)).findMapping(any(MappingDTO.class));
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
    when(tenantServices.getById(any())).thenThrow(new RuntimeException());
    when(mappingServices.createMapping(any()))
        .thenAnswer(invocation -> CompletableFuture.completedFuture(new MappingRecord()));

    // when
    migrationHandler.migrate();

    // then
    verify(managementIdentityClient, times(2)).fetchTenantMappingRules(anyInt());
    verify(tenantServices, times(2)).getById(any());
    verify(mappingServices, times(2)).findMapping(any(MappingDTO.class));
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
    when(tenantServices.getById(any())).thenReturn(new TenantEntity(1L, "", "", null));
    when(mappingServices.createMapping(any()))
        .thenAnswer(invocation -> CompletableFuture.completedFuture(new MappingRecord()));
    when(tenantServices.addMember(anyString(), any(), anyString()))
        .thenReturn(
            CompletableFuture.failedFuture(
                new BrokerRejectionException(
                    new BrokerRejection(
                        TenantIntent.ADD_ENTITY,
                        -1,
                        RejectionType.ALREADY_EXISTS,
                        "entity already assigned to tenant"))));

    // when
    migrationHandler.migrate();

    // then
    verify(managementIdentityClient, times(2)).fetchTenantMappingRules(anyInt());
    verify(tenantServices, times(4)).getById(any());
    verify(tenantServices, times(4)).addMember(anyString(), any(), anyString());
    verify(mappingServices, times(2)).findMapping(any(MappingDTO.class));
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
