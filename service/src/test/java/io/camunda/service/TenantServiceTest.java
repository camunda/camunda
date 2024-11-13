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
import io.camunda.service.TenantServices.TenantDTO;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.tenant.BrokerTenantCreateRequest;
import io.camunda.zeebe.gateway.impl.broker.request.tenant.BrokerTenantUpdateRequest;
import io.camunda.zeebe.protocol.impl.record.value.tenant.TenantRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.TenantIntent;
import java.util.List;
import org.assertj.core.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class TenantServiceTest {

  private TenantServices services;
  private TenantSearchClient client;
  private Authentication authentication;
  private StubbedBrokerClient stubbedBrokerClient;

  @BeforeEach
  public void before() {
    stubbedBrokerClient = new StubbedBrokerClient();
    authentication = Authentication.of(builder -> builder.user(1234L).token("auth_token"));
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
    final var entity = mock(TenantEntity.class);
    final var result = new SearchQueryResult<>(1, List.of(entity), Arrays.array());
    when(client.searchTenants(any())).thenReturn(result);

    // when
    final var searchQueryResult = services.getByTenantKey(1L);

    // then
    assertThat(searchQueryResult).isEqualTo(entity);
  }

  @Test
  public void shouldReturnSingleVariableForGet() {
    // given
    final var entity = mock(TenantEntity.class);
    final var result = new SearchQueryResult<>(1, List.of(entity), Arrays.array());
    when(client.searchTenants(any())).thenReturn(result);

    // when
    final var searchQueryResult = services.getByTenantKey(1L);

    // then
    assertThat(searchQueryResult).isEqualTo(entity);
  }

  @Test
  public void shouldThrowExceptionIfNotFoundByKey() {
    // given
    final var key = 100L;
    when(client.searchTenants(any())).thenReturn(new SearchQueryResult(0, List.of(), null));

    // when / then
    final var exception =
        assertThrowsExactly(NotFoundException.class, () -> services.getByTenantKey(key));
    assertThat(exception.getMessage()).isEqualTo("Tenant with tenantKey 100 not found");
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
    assertThat(request.getKey()).isEqualTo(tenantDTO.tenantKey());
    final TenantRecord brokerRequestValue = request.getRequestWriter();
    assertThat(brokerRequestValue.getName()).isEqualTo(tenantDTO.name());
  }
}
