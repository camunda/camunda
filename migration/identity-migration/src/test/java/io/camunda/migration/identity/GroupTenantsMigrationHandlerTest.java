/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.camunda.migration.identity.dto.GroupTenants;
import io.camunda.migration.identity.dto.MigrationStatusUpdateRequest;
import io.camunda.migration.identity.dto.Tenant;
import io.camunda.migration.identity.midentity.ManagementIdentityClient;
import io.camunda.search.entities.GroupEntity;
import io.camunda.search.entities.TenantEntity;
import io.camunda.service.GroupServices;
import io.camunda.service.TenantServices;
import io.camunda.zeebe.broker.client.api.BrokerRejectionException;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class GroupTenantsMigrationHandlerTest {

  @Mock private ManagementIdentityClient managementIdentityClient;

  @Mock(answer = Answers.RETURNS_SELF)
  private GroupServices groupServices;

  @Mock(answer = Answers.RETURNS_SELF)
  private TenantServices tenantServices;

  @InjectMocks private GroupTenantsMigrationHandler handler;

  @Test
  void stopWhenNoMoreRecords() {
    // given
    givenGroupTenants();
    when(tenantServices.getById(any()))
        .thenReturn(new TenantEntity(1L, "", "", Collections.emptySet()));
    when(groupServices.findGroup(anyString()))
        .thenReturn(Optional.of(new GroupEntity(1L, "", Collections.emptySet())));
    when(tenantServices.addMember(any(), any(), anyLong()))
        .thenReturn(CompletableFuture.completedFuture(new TenantRecord()));

    // when
    handler.migrate();

    // then
    verify(managementIdentityClient, times(2)).fetchGroupTenants(anyInt());
    verify(tenantServices, times(4)).getById(any());
    verify(groupServices, times(2)).findGroup(anyString());
    verify(tenantServices, times(4)).addMember(any(), any(), anyLong());
  }

  @Test
  void setErrorWhenGroupCantBeCreated() {
    // given
    givenGroupTenants();
    when(groupServices.findGroup(anyString())).thenReturn(Optional.empty());

    // when
    handler.migrate();

    // then
    verify(managementIdentityClient, times(2)).fetchGroupTenants(anyInt());
    verify(groupServices, times(2)).findGroup(anyString());
    verify(tenantServices, never()).getById(any());
    verify(managementIdentityClient, times(1))
        .updateMigrationStatus(
            assertArg(
                migrationStatusUpdateRequests -> {
                  assertThat(migrationStatusUpdateRequests)
                      .describedAs("All migrations have failed")
                      .noneMatch(MigrationStatusUpdateRequest::success);
                }));
  }

  @Test
  void setErrorWhenTenantNotFound() {
    // given
    givenGroupTenants();
    when(groupServices.findGroup(anyString()))
        .thenReturn(Optional.of(new GroupEntity(1L, "", Collections.emptySet())));
    when(tenantServices.getById(any())).thenThrow(new RuntimeException());

    // when
    handler.migrate();

    // then
    verify(managementIdentityClient, times(2)).fetchGroupTenants(anyInt());
    verify(groupServices, times(2)).findGroup(anyString());
    verify(tenantServices, times(2)).getById(any());
    verify(managementIdentityClient, times(1))
        .updateMigrationStatus(
            assertArg(
                migrationStatusUpdateRequests -> {
                  assertThat(migrationStatusUpdateRequests)
                      .describedAs("All migrations have failed")
                      .noneMatch(MigrationStatusUpdateRequest::success);
                }));
  }

  @Test
  void ignoreWhenGroupAlreadyAssigned() {
    // given
    givenGroupTenants();
    when(groupServices.findGroup(anyString()))
        .thenReturn(Optional.of(new GroupEntity(1L, "", Collections.emptySet())));
    when(tenantServices.getById(any()))
        .thenReturn(new TenantEntity(1L, "", "", Collections.emptySet()));
    doThrow(
            new BrokerRejectionException(
                new BrokerRejection(TenantIntent.ADD_ENTITY, -1, RejectionType.ALREADY_EXISTS, "")))
        .when(tenantServices)
        .addMember(any(), any(), anyLong());

    // when
    handler.migrate();

    // then
    verify(managementIdentityClient, times(2)).fetchGroupTenants(anyInt());
    verify(groupServices, times(2)).findGroup(anyString());
    verify(tenantServices, times(4)).getById(any());
    verify(tenantServices, times(4)).addMember(any(), any(), anyLong());
    verify(managementIdentityClient, times(1))
        .updateMigrationStatus(
            assertArg(
                migrationStatusUpdateRequests ->
                    assertThat(migrationStatusUpdateRequests)
                        .describedAs("All migrations have succeeded")
                        .allMatch(MigrationStatusUpdateRequest::success)));
  }

  private void givenGroupTenants() {
    when(managementIdentityClient.fetchGroupTenants(anyInt()))
        .thenReturn(
            List.of(
                new GroupTenants(
                    "group1", "group1", List.of(new Tenant("id1", "t1"), new Tenant("id2", "t2"))),
                new GroupTenants(
                    "group2", "group2", List.of(new Tenant("id1", "t1"), new Tenant("id3", "t3")))))
        .thenReturn(List.of());
  }
}
