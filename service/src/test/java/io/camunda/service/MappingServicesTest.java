/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.MappingSearchClient;
import io.camunda.search.entities.MappingEntity;
import io.camunda.search.exception.NotFoundException;
import io.camunda.search.filter.MappingFilter;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.Authentication;
import io.camunda.service.MappingServices.MappingDTO;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerMappingCreateRequest;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.MappingIntent;
import java.util.List;
import org.assertj.core.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MappingServicesTest {

  private MappingServices services;
  private MappingSearchClient client;
  private Authentication authentication;
  private StubbedBrokerClient stubbedBrokerClient;

  @BeforeEach
  public void before() {
    authentication = Authentication.of(builder -> builder.user(1234L).token("auth_token"));
    stubbedBrokerClient = new StubbedBrokerClient();
    client = mock(MappingSearchClient.class);
    when(client.withSecurityContext(any())).thenReturn(client);
    services =
        new MappingServices(
            stubbedBrokerClient, mock(SecurityContextProvider.class), client, authentication);
  }

  @Test
  public void shouldCreateMapping() {
    // given
    final var mappingDTO = new MappingDTO(100L, "newClaimName", "newClaimValue");

    // when
    services.createMapping(mappingDTO);

    // then
    final BrokerMappingCreateRequest request = stubbedBrokerClient.getSingleBrokerRequest();
    assertThat(request.getIntent()).isEqualTo(MappingIntent.CREATE);
    assertThat(request.getValueType()).isEqualTo(ValueType.MAPPING);
    final MappingRecord brokerRequestValue = request.getRequestWriter();
    assertThat(brokerRequestValue.getClaimName()).isEqualTo(mappingDTO.claimName());
    assertThat(brokerRequestValue.getClaimValue()).isEqualTo(mappingDTO.claimValue());
  }

  @Test
  public void shouldEmptyQueryReturnRoles() {
    // given
    final var result = mock(SearchQueryResult.class);
    when(client.searchMappings(any())).thenReturn(result);

    final MappingFilter filter = new MappingFilter.Builder().build();
    final var searchQuery = SearchQueryBuilders.mappingSearchQuery((b) -> b.filter(filter));

    // when
    final var searchQueryResult = services.search(searchQuery);

    // then
    assertThat(searchQueryResult).isEqualTo(result);
  }

  @Test
  public void shouldReturnSingleVariable() {
    // given
    final var entity = mock(MappingEntity.class);
    final var result = new SearchQueryResult<>(1, List.of(entity), Arrays.array());
    when(client.searchMappings(any())).thenReturn(result);
  }

  @Test
  public void shouldReturnSingleVariableForFind() {
    // given
    final var entity = mock(MappingEntity.class);
    final var result = new SearchQueryResult<>(1, List.of(entity), Arrays.array());
    when(client.searchMappings(any())).thenReturn(result);

    // when
    final var searchQueryResult = services.findMapping(1L);

    // then
    assertThat(searchQueryResult).contains(entity);
  }

  @Test
  public void shouldReturnEmptyWhenNotFoundByFind() {
    // given
    final var key = 100L;
    when(client.searchMappings(any())).thenReturn(new SearchQueryResult(0, List.of(), null));

    // when / then
    assertThat(services.findMapping(key)).isEmpty();
  }

  @Test
  public void shouldThrowExceptionWhenNotFoundByGet() {
    // given
    when(client.searchMappings(any())).thenReturn(new SearchQueryResult(0, List.of(), null));

    // when / then
    assertThrows(NotFoundException.class, () -> services.getMapping(1L));
  }
}
