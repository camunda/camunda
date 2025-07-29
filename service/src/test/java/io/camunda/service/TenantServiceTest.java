/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.TenantSearchClient;
import io.camunda.search.entities.TenantEntity;
import io.camunda.search.exception.ResourceAccessDeniedException;
import io.camunda.search.filter.TenantFilter;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.TenantServices.TenantMemberRequest;
import io.camunda.service.TenantServices.TenantRequest;
import io.camunda.service.authorization.Authorizations;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerTenantEntityRequest;
import io.camunda.zeebe.gateway.impl.broker.request.tenant.BrokerTenantCreateRequest;
import io.camunda.zeebe.gateway.impl.broker.request.tenant.BrokerTenantDeleteRequest;
import io.camunda.zeebe.gateway.impl.broker.request.tenant.BrokerTenantUpdateRequest;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import io.camunda.zeebe.protocol.record.value.EntityType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

public class TenantServiceTest {

  private TenantServices services;
  private TenantSearchClient client;
  private StubbedBrokerClient stubbedBrokerClient;
  private final TenantEntity tenantEntity =
      new TenantEntity(100L, "tenant-id", "Tenant name", "Tenant description");

  @BeforeEach
  public void before() {
    stubbedBrokerClient = new StubbedBrokerClient();
    final CamundaAuthentication authentication =
        CamundaAuthentication.of(builder -> builder.user("foo"));
    client = mock(TenantSearchClient.class);
    when(client.withSecurityContext(any())).thenReturn(client);
    services =
        new TenantServices(
            stubbedBrokerClient, mock(SecurityContextProvider.class), client, authentication);
  }

  @Test
  public void shouldEmptyQueryReturnTenants() {
    // given
    final var result = mock(SearchQueryResult.class);
    when(client.searchTenants(any())).thenReturn(result);

    final TenantFilter filter = new TenantFilter.Builder().build();
    final var searchQuery = SearchQueryBuilders.tenantSearchQuery((b) -> b.filter(filter));

    // when
    final var searchQueryResult = services.search(searchQuery);

    // then
    assertThat(searchQueryResult).isEqualTo(result);
  }

  @Test
  public void shouldReturnSingleTenant() {
    // given
    when(client.getTenant(eq("tenant-id"))).thenReturn(tenantEntity);

    // when
    final var searchQueryResult = services.getById("tenant-id");

    // then
    assertThat(searchQueryResult).isEqualTo(tenantEntity);
  }

  @Test
  public void shouldCreateTenant() {
    // given
    final var tenantDTO =
        new TenantRequest(100L, "NewTenantName", "NewTenantId", "NewTenantDescription");

    // when
    services.createTenant(tenantDTO);

    // then
    final BrokerTenantCreateRequest request = stubbedBrokerClient.getSingleBrokerRequest();
    assertThat(request.getIntent()).isEqualTo(TenantIntent.CREATE);
    assertThat(request.getValueType()).isEqualTo(ValueType.TENANT);
    final TenantRecord brokerRequestValue = request.getRequestWriter();
    assertThat(brokerRequestValue.getName()).isEqualTo(tenantDTO.name());
    assertThat(brokerRequestValue.getTenantId()).isEqualTo(tenantDTO.tenantId());
  }

  @Test
  public void shouldUpdateTenantName() {

    // given
    final var tenantDTO =
        new TenantRequest(tenantEntity.key(), tenantEntity.tenantId(), "UpdatedTenantId", null);

    // when
    services.updateTenant(tenantDTO);

    // then
    final BrokerTenantUpdateRequest request = stubbedBrokerClient.getSingleBrokerRequest();
    assertThat(request.getIntent()).isEqualTo(TenantIntent.UPDATE);
    assertThat(request.getValueType()).isEqualTo(ValueType.TENANT);
    final TenantRecord brokerRequestValue = request.getRequestWriter();
    assertThat(brokerRequestValue.getTenantId()).isEqualTo(tenantDTO.tenantId());
    assertThat(brokerRequestValue.getName()).isEqualTo(tenantDTO.name());
  }

  @Test
  public void shouldUpdateTenantDescription() {

    // given
    final var tenantDTO =
        new TenantRequest(
            tenantEntity.key(), tenantEntity.tenantId(), "TenantName", "UpdatedTenantDescription");

    // when
    services.updateTenant(tenantDTO);

    // then
    final BrokerTenantUpdateRequest request = stubbedBrokerClient.getSingleBrokerRequest();
    assertThat(request.getIntent()).isEqualTo(TenantIntent.UPDATE);
    assertThat(request.getValueType()).isEqualTo(ValueType.TENANT);
    final TenantRecord brokerRequestValue = request.getRequestWriter();
    assertThat(brokerRequestValue.getTenantId()).isEqualTo(tenantDTO.tenantId());
    assertThat(brokerRequestValue.getDescription()).isEqualTo(tenantDTO.description());
  }

  @Test
  public void shouldDeleteTenant() {
    // when
    services.deleteTenant(tenantEntity.tenantId());

    // then
    final BrokerTenantDeleteRequest request = stubbedBrokerClient.getSingleBrokerRequest();
    assertThat(request.getIntent()).isEqualTo(TenantIntent.DELETE);
    assertThat(request.getValueType()).isEqualTo(ValueType.TENANT);
    assertThat(request.getRequestWriter().getTenantId()).isEqualTo(tenantEntity.tenantId());
  }

  @ParameterizedTest
  @EnumSource(
      value = EntityType.class,
      names = {"USER", "MAPPING_RULE", "GROUP", "ROLE", "CLIENT"})
  public void shouldAddEntityToTenant(final EntityType entityType) {
    // given
    final var tenantId = "tenantId";
    final var entityId = "entityId";
    final var tenantMemberRequest = new TenantMemberRequest(tenantId, entityId, entityType);

    // when
    services.addMember(tenantMemberRequest);

    // then
    final BrokerTenantEntityRequest request = stubbedBrokerClient.getSingleBrokerRequest();
    assertThat(request.getIntent()).isEqualTo(TenantIntent.ADD_ENTITY);
    assertThat(request.getValueType()).isEqualTo(ValueType.TENANT);
    final TenantRecord brokerRequestValue = request.getRequestWriter();
    assertThat(brokerRequestValue.getTenantId()).isEqualTo(tenantId);
    assertThat(brokerRequestValue.getEntityId()).isEqualTo(entityId);
    assertThat(brokerRequestValue.getEntityType()).isEqualTo(entityType);
  }

  @ParameterizedTest
  @EnumSource(
      value = EntityType.class,
      names = {"USER", "MAPPING_RULE", "GROUP", "ROLE", "CLIENT"})
  public void shouldRemoveEntityFromTenant(final EntityType entityType) {
    // given
    final var tenantId = "tenantId";
    final var entityId = "entityId";
    final var tenantMemberRequest = new TenantMemberRequest(tenantId, entityId, entityType);

    // when
    services.removeMember(tenantMemberRequest);

    // then
    final BrokerTenantEntityRequest request = stubbedBrokerClient.getSingleBrokerRequest();
    assertThat(request.getIntent()).isEqualTo(TenantIntent.REMOVE_ENTITY);
    assertThat(request.getValueType()).isEqualTo(ValueType.TENANT);
    final TenantRecord brokerRequestValue = request.getRequestWriter();
    assertThat(brokerRequestValue.getTenantId()).isEqualTo(tenantId);
    assertThat(brokerRequestValue.getEntityId()).isEqualTo(entityId);
    assertThat(brokerRequestValue.getEntityType()).isEqualTo(entityType);
  }

  @Test
  public void shouldThrowForbiddenIfNotAuthorized() {
    when(client.getTenant(eq("tenant-id")))
        .thenThrow(new ResourceAccessDeniedException(Authorizations.TENANT_READER_AUTHORIZATION));

    final var exception =
        (ServiceException)
            assertThatThrownBy(() -> services.getById("tenant-id"))
                .isInstanceOf(ServiceException.class)
                .actual();
    assertThat(exception.getStatus()).isEqualTo(Status.FORBIDDEN);
  }
}
