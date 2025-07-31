/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.migration.identity.handler.sm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.migration.identity.client.ManagementIdentityClient;
import io.camunda.migration.identity.config.IdentityMigrationProperties;
import io.camunda.migration.identity.config.IdentityMigrationProperties.Mode;
import io.camunda.migration.identity.dto.Tenant;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.TenantServices;
import io.camunda.service.TenantServices.TenantMemberRequest;
import io.camunda.service.TenantServices.TenantRequest;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.zeebe.broker.client.api.BrokerErrorException;
import io.camunda.zeebe.broker.client.api.BrokerRejectionException;
import io.camunda.zeebe.broker.client.api.dto.BrokerError;
import io.camunda.zeebe.broker.client.api.dto.BrokerRejection;
import io.camunda.zeebe.protocol.record.ErrorCode;
import io.camunda.zeebe.protocol.record.RejectionType;
import io.camunda.zeebe.protocol.record.intent.GroupIntent;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class OidcTenantMigrationHandlerTest {

  private final ManagementIdentityClient managementIdentityClient;
  private final TenantServices tenantServices;
  private final TenantMigrationHandler migrationHandler;

  public OidcTenantMigrationHandlerTest(
      @Mock final ManagementIdentityClient managementIdentityClient,
      @Mock(answer = Answers.RETURNS_SELF) final TenantServices tenantServices) {
    this.managementIdentityClient = managementIdentityClient;
    this.tenantServices = tenantServices;
    final var migrationProperties = new IdentityMigrationProperties();
    migrationProperties.setMode(Mode.OIDC);
    migrationProperties.setBackpressureDelay(100);
    migrationHandler =
        new TenantMigrationHandler(
            managementIdentityClient,
            tenantServices,
            CamundaAuthentication.none(),
            migrationProperties);
  }

  @Test
  public void shouldMigrateTenants() {
    // given
    when(managementIdentityClient.fetchTenants())
        .thenReturn(
            List.of(
                new Tenant("tenant1", "Tenant 1"),
                new Tenant("tenant2", "Tenant 2"),
                new Tenant("<default>", "Default Tenant")));
    when(tenantServices.createTenant(any(TenantRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when
    migrationHandler.migrate();

    // then
    final var tenantCapture = ArgumentCaptor.forClass(TenantRequest.class);
    verify(tenantServices, times(2)).createTenant(tenantCapture.capture());
    final var capturedTenants = tenantCapture.getAllValues();
    assertThat(capturedTenants).hasSize(2);
    assertThat(capturedTenants.getFirst().tenantId()).isEqualTo("tenant1");
    assertThat(capturedTenants.getFirst().name()).isEqualTo("Tenant 1");
    assertThat(capturedTenants.getLast().tenantId()).isEqualTo("tenant2");
    assertThat(capturedTenants.getLast().name()).isEqualTo("Tenant 2");
    verify(tenantServices, times(0)).addMember(any(TenantMemberRequest.class));
  }

  @Test
  public void shouldContinueMigrationWithFetchTenantsEndpointsUnavailable() {
    when(managementIdentityClient.fetchTenants())
        .thenThrow(new NotImplementedException("Tenants endpoint unavailable"));

    // when
    migrationHandler.migrate();

    // then
    verify(tenantServices, times(0)).createTenant(any(TenantRequest.class));
    verify(managementIdentityClient, times(1)).fetchTenants();
  }

  @Test
  public void shouldContinueMigrationIfConflicts() {
    // given
    when(managementIdentityClient.fetchTenants())
        .thenReturn(List.of(new Tenant("tenant1", "Tenant 1"), new Tenant("tenant2", "Tenant 2")));
    when(tenantServices.createTenant(any(TenantRequest.class)))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapError(
                    new BrokerRejectionException(
                        new BrokerRejection(
                            GroupIntent.CREATE,
                            -1,
                            RejectionType.ALREADY_EXISTS,
                            "tenant already exists")))));

    // when
    migrationHandler.migrate();

    // then
    verify(tenantServices, times(2)).createTenant(any(TenantRequest.class));
    verify(tenantServices, times(0)).addMember(any(TenantMemberRequest.class));
  }

  @Test
  public void shouldRetryWithBackoffOnTenantCreation() {
    // given
    when(managementIdentityClient.fetchTenants())
        .thenReturn(
            List.of(
                new Tenant("tenant1", "Tenant 1"),
                new Tenant("tenant2", "Tenant 2"),
                new Tenant("<default>", "Default Tenant")));
    when(tenantServices.createTenant(any(TenantRequest.class)))
        .thenReturn(
            CompletableFuture.failedFuture(
                ErrorMapper.mapError(
                    new BrokerErrorException(
                        new BrokerError(ErrorCode.RESOURCE_EXHAUSTED, "backpressure")))))
        .thenReturn(CompletableFuture.completedFuture(null));

    // when
    migrationHandler.migrate();

    // then
    verify(tenantServices, times(3)).createTenant(any(TenantRequest.class));
    verify(tenantServices, times(0)).addMember(any(TenantMemberRequest.class));
  }
}
