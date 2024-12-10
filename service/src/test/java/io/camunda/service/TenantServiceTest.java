/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrowsExactly;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.TenantSearchClient;
import io.camunda.search.entities.TenantEntity;
import io.camunda.search.exception.NotFoundException;
import io.camunda.search.filter.TenantFilter;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.Authentication;
import io.camunda.security.auth.Authorization;
import io.camunda.service.TenantServices.TenantDTO;
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
import java.util.List;
import java.util.Set;
import org.assertj.core.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

public class TenantServiceTest {

  private TenantServices services;
  private TenantSearchClient client;
  private StubbedBrokerClient stubbedBrokerClient;
  private final TenantEntity tenantEntity =
      new TenantEntity(100L, "tenant-id", "Tenant name", Set.of());

  @BeforeEach
  public void before() {
    stubbedBrokerClient = new StubbedBrokerClient();
    final Authentication authentication =
        Authentication.of(builder -> builder.user(1234L).token("auth_token"));
    client = mock(TenantSearchClient.class);
    final SecurityContextProvider securityContextProvider = mock(SecurityContextProvider.class);
    when(client.withSecurityContext(any())).thenReturn(client);
    when(securityContextProvider.isAuthorized(
            "tenant-id", authentication, Authorization.of(a -> a.tenant().read())))
        .thenReturn(true);
    services =
        new TenantServices(stubbedBrokerClient, securityContextProvider, client, authentication);
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
    final var result = new SearchQueryResult<>(1, List.of(tenantEntity), Arrays.array());
    when(client.searchTenants(any())).thenReturn(result);

    // when
    final var searchQueryResult = services.getByKey(100L);

    // then
    assertThat(searchQueryResult).isEqualTo(tenantEntity);
  }

  @Test
  public void shouldReturnSingleVariableForGet() {
    // given
    final var result = new SearchQueryResult<>(1, List.of(tenantEntity), Arrays.array());
    when(client.searchTenants(any())).thenReturn(result);

    // when
    final var searchQueryResult = services.getByKey(100L);

    // then
    assertThat(searchQueryResult).isEqualTo(tenantEntity);
  }

  @Test
  public void shouldThrowExceptionIfNotFoundByKey() {
    // given
    final var key = 100L;
    when(client.searchTenants(any())).thenReturn(new SearchQueryResult(0, List.of(), null));

    // when / then
    final var exception =
        assertThrowsExactly(NotFoundException.class, () -> services.getByKey(key));
    assertThat(exception.getMessage()).isEqualTo("Tenant with key 100 not found");
  }

  @Test
  public void shouldCreateTenant() {
    // given
    final var tenantDTO = new TenantDTO(100L, "NewTenantName", "NewTenantId");

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
    final var tenantDTO = new TenantDTO(100L, "ignored", "UpdatedTenantId");

    // when
    services.updateTenant(tenantDTO);

    // then
    final BrokerTenantUpdateRequest request = stubbedBrokerClient.getSingleBrokerRequest();
    assertThat(request.getIntent()).isEqualTo(TenantIntent.UPDATE);
    assertThat(request.getValueType()).isEqualTo(ValueType.TENANT);
    final TenantRecord brokerRequestValue = request.getRequestWriter();
    assertThat(brokerRequestValue.getTenantKey()).isEqualTo(tenantDTO.key());
    assertThat(brokerRequestValue.getName()).isEqualTo(tenantDTO.name());
  }

  @Test
  public void shouldDeleteTenant() {
    // when
    services.deleteTenant(100L);

    // then
    final BrokerTenantDeleteRequest request = stubbedBrokerClient.getSingleBrokerRequest();
    assertThat(request.getIntent()).isEqualTo(TenantIntent.DELETE);
    assertThat(request.getValueType()).isEqualTo(ValueType.TENANT);
    assertThat(request.getRequestWriter().getTenantKey()).isEqualTo(100L);
  }

  @ParameterizedTest
  @EnumSource(
      value = EntityType.class,
      names = {"USER", "MAPPING", "GROUP"})
  public void shouldAddEntityToTenant(final EntityType entityType) {
    // given
    final var tenantKey = 100L;
    final var entityKey = 42;

    // when
    services.addMember(tenantKey, entityType, entityKey);

    // then
    final BrokerTenantEntityRequest request = stubbedBrokerClient.getSingleBrokerRequest();
    assertThat(request.getIntent()).isEqualTo(TenantIntent.ADD_ENTITY);
    assertThat(request.getValueType()).isEqualTo(ValueType.TENANT);
    final TenantRecord brokerRequestValue = request.getRequestWriter();
    assertThat(brokerRequestValue.getTenantKey()).isEqualTo(tenantKey);
    assertThat(brokerRequestValue.getEntityKey()).isEqualTo(entityKey);
    assertThat(brokerRequestValue.getEntityType()).isEqualTo(entityType);
  }

  @ParameterizedTest
  @EnumSource(
      value = EntityType.class,
      names = {"USER", "MAPPING", "GROUP"})
  public void shouldRemoveEntityFromTenant(final EntityType entityType) {
    // given
    final var tenantKey = 100L;
    final var entityKey = 42;

    // when
    services.removeMember(tenantKey, entityType, entityKey);

    // then
    final BrokerTenantEntityRequest request = stubbedBrokerClient.getSingleBrokerRequest();
    assertThat(request.getIntent()).isEqualTo(TenantIntent.REMOVE_ENTITY);
    assertThat(request.getValueType()).isEqualTo(ValueType.TENANT);
    final TenantRecord brokerRequestValue = request.getRequestWriter();
    assertThat(brokerRequestValue.getTenantKey()).isEqualTo(tenantKey);
    assertThat(brokerRequestValue.getEntityKey()).isEqualTo(entityKey);
    assertThat(brokerRequestValue.getEntityType()).isEqualTo(entityType);
  }
}
