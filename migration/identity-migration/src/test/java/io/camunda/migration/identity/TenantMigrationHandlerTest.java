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
import static org.mockito.ArgumentMatchers.assertArg;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.camunda.migration.identity.dto.MigrationStatusUpdateRequest;
import io.camunda.migration.identity.dto.Tenant;
import io.camunda.migration.identity.midentity.ManagementIdentityClient;
import io.camunda.migration.identity.midentity.ManagementIdentityTransformer;
import io.camunda.security.auth.Authentication;
import io.camunda.service.TenantServices;
import io.camunda.zeebe.broker.client.api.BrokerRejectionException;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
final class TenantMigrationHandlerTest {
  private final ManagementIdentityClient managementIdentityClient;
  private final TenantServices tenantServices;
  private final TenantMigrationHandler migrationHandler;

  public TenantMigrationHandlerTest(
      @Mock final ManagementIdentityClient managementIdentityClient,
      @Mock(answer = Answers.RETURNS_SELF) final TenantServices tenantServices) {
    this.managementIdentityClient = managementIdentityClient;
    this.tenantServices = tenantServices;
    migrationHandler =
        new TenantMigrationHandler(
            Authentication.none(),
            managementIdentityClient,
            new ManagementIdentityTransformer(),
            this.tenantServices);
  }

  @Test
  void stopWhenIdentityEndpointNotFound() {
    when(managementIdentityClient.fetchTenants(anyInt())).thenThrow(new NotImplementedException());

    // when
    assertThrows(NotImplementedException.class, migrationHandler::migrate);

    // then
    verify(managementIdentityClient).fetchTenants(anyInt());
    verifyNoMoreInteractions(managementIdentityClient);
  }

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
    verify(tenantServices, times(2)).createTenant(any());
  }

  @Test
  void ignoreWhenTenantAlreadyExists() {
    // given
    when(tenantServices.createTenant(any()))
        .thenReturn(
            CompletableFuture.failedFuture(
                new BrokerRejectionException(
                    new BrokerRejection(
                        TenantIntent.CREATE,
                        -1,
                        RejectionType.ALREADY_EXISTS,
                        "tenant already exists"))));
    when(managementIdentityClient.fetchTenants(anyInt()))
        .thenReturn(List.of(new Tenant("id1", "t1"), new Tenant("id2", "t2")))
        .thenReturn(List.of());

    // when
    migrationHandler.migrate();

    // then
    verify(managementIdentityClient, times(2)).fetchTenants(anyInt());
    verify(tenantServices, times(2)).createTenant(any());
    verify(managementIdentityClient, times(2))
        .updateMigrationStatus(
            assertArg(
                migrationStatusUpdateRequests -> {
                  assertThat(migrationStatusUpdateRequests)
                      .describedAs("All requests should succeed")
                      .allMatch(MigrationStatusUpdateRequest::success);
                }));
  }

  @Test
  void setErrorWhenTenantCreationHasError() {
    // given
    when(tenantServices.createTenant(any())).thenThrow(new RuntimeException());
    when(managementIdentityClient.fetchTenants(anyInt()))
        .thenReturn(List.of(new Tenant("id1", "t1"), new Tenant("id2", "t2")))
        .thenReturn(List.of());

    // when
    migrationHandler.migrate();

    // then
    verify(managementIdentityClient, times(2))
        .updateMigrationStatus(
            assertArg(
                migrationStatusUpdateRequests -> {
                  assertThat(migrationStatusUpdateRequests)
                      .describedAs("All requests should fail")
                      .noneMatch(MigrationStatusUpdateRequest::success);
                }));
    verify(managementIdentityClient, times(2)).fetchTenants(anyInt());
    verify(tenantServices, times(2)).createTenant(any());
  }
}
