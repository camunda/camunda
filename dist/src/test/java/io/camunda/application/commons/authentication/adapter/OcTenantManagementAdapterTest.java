/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.application.commons.authentication.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.auth.domain.model.CamundaAuthentication;
import io.camunda.auth.domain.model.MemberType;
import io.camunda.auth.domain.spi.CamundaAuthenticationProvider;
import io.camunda.search.entities.TenantEntity;
import io.camunda.service.TenantServices;
import io.camunda.service.TenantServices.TenantMemberRequest;
import io.camunda.service.TenantServices.TenantRequest;
import io.camunda.zeebe.protocol.record.value.EntityType;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class OcTenantManagementAdapterTest {

  private final TenantServices tenantServices = mock(TenantServices.class);
  private final TenantServices authenticatedTenantServices = mock(TenantServices.class);
  private final CamundaAuthenticationProvider authProvider =
      mock(CamundaAuthenticationProvider.class);
  private final CamundaAuthentication authentication = CamundaAuthentication.none();

  private OcTenantManagementAdapter adapter;

  @BeforeEach
  void setUp() {
    when(authProvider.getCamundaAuthentication()).thenReturn(authentication);
    when(tenantServices.withAuthentication(authentication)).thenReturn(authenticatedTenantServices);
    adapter = new OcTenantManagementAdapter(tenantServices, authProvider);
  }

  @Test
  void getByIdDelegatesToTenantServicesGetById() {
    final var entity = new TenantEntity(7L, "t-1", "Tenant One", "First tenant");
    when(authenticatedTenantServices.getById("t-1")).thenReturn(entity);

    final var result = adapter.getById("t-1");

    assertThat(result.tenantKey()).isEqualTo(7L);
    assertThat(result.tenantId()).isEqualTo("t-1");
    assertThat(result.name()).isEqualTo("Tenant One");
    assertThat(result.description()).isEqualTo("First tenant");
  }

  @Test
  void createDelegatesToTenantServicesCreateTenant() {
    when(authenticatedTenantServices.createTenant(any(TenantRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(null));

    final var result = adapter.create("t-2", "Tenant Two", "Second tenant");

    assertThat(result.tenantId()).isEqualTo("t-2");
    assertThat(result.name()).isEqualTo("Tenant Two");

    final var captor = ArgumentCaptor.forClass(TenantRequest.class);
    verify(authenticatedTenantServices).createTenant(captor.capture());
    assertThat(captor.getValue().tenantId()).isEqualTo("t-2");
    assertThat(captor.getValue().key()).isNull();
  }

  @Test
  void deleteDelegatesToTenantServicesDeleteTenant() {
    when(authenticatedTenantServices.deleteTenant("t-1"))
        .thenReturn(CompletableFuture.completedFuture(null));

    adapter.delete("t-1");

    verify(authenticatedTenantServices).deleteTenant("t-1");
  }

  @Test
  void addMemberMapsEntityTypeCorrectly() {
    when(authenticatedTenantServices.addMember(any(TenantMemberRequest.class)))
        .thenReturn(CompletableFuture.completedFuture(null));

    adapter.addMember("t-1", "alice", MemberType.USER);

    final var captor = ArgumentCaptor.forClass(TenantMemberRequest.class);
    verify(authenticatedTenantServices).addMember(captor.capture());
    assertThat(captor.getValue().tenantId()).isEqualTo("t-1");
    assertThat(captor.getValue().entityId()).isEqualTo("alice");
    assertThat(captor.getValue().entityType()).isEqualTo(EntityType.USER);
  }

  @Test
  void mapsNullTenantKeyToZero() {
    final var entity = new TenantEntity(null, "t-x", "TX", null);
    when(authenticatedTenantServices.getById("t-x")).thenReturn(entity);

    final var result = adapter.getById("t-x");

    assertThat(result.tenantKey()).isEqualTo(0L);
  }

  @Test
  void createUnwrapsRuntimeCompletionException() {
    final var rootCause = new IllegalStateException("conflict");
    when(authenticatedTenantServices.createTenant(any(TenantRequest.class)))
        .thenReturn(CompletableFuture.failedFuture(rootCause));

    assertThatThrownBy(() -> adapter.create("t-1", "T1", "desc"))
        .isInstanceOf(IllegalStateException.class)
        .hasMessage("conflict");
  }
}
