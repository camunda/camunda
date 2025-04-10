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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.MappingSearchClient;
import io.camunda.search.entities.MappingEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.filter.MappingFilter;
import io.camunda.search.filter.MappingFilter.Claim;
import io.camunda.search.query.MappingQuery;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.Authentication;
import io.camunda.service.MappingServices.MappingDTO;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerMappingCreateRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerMappingDeleteRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerMappingUpdateRequest;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.MappingIntent;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.assertj.core.util.Arrays;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class MappingServicesTest {

  ArgumentCaptor<BrokerMappingDeleteRequest> mappingDeleteRequestArgumentCaptor;
  ArgumentCaptor<BrokerMappingUpdateRequest> mappingUpdateRequestArgumentCaptor;
  private MappingServices services;
  private MappingSearchClient client;
  private Authentication authentication;
  private StubbedBrokerClient stubbedBrokerClient;

  @BeforeEach
  public void before() {
    authentication = Authentication.of(builder -> builder.user("foo"));
    stubbedBrokerClient = new StubbedBrokerClient();
    client = mock(MappingSearchClient.class);
    when(client.withSecurityContext(any())).thenReturn(client);
    mappingDeleteRequestArgumentCaptor = ArgumentCaptor.forClass(BrokerMappingDeleteRequest.class);
    mappingUpdateRequestArgumentCaptor = ArgumentCaptor.forClass(BrokerMappingUpdateRequest.class);
    services =
        new MappingServices(
            stubbedBrokerClient, mock(SecurityContextProvider.class), client, authentication);
  }

  @Test
  public void shouldCreateMapping() {
    // given
    final var mappingDTO =
        new MappingDTO("newClaimName", "newClaimValue", "mappingRuleName", "mappingRuleId");

    // when
    services.createMapping(mappingDTO);

    // then
    final BrokerMappingCreateRequest request = stubbedBrokerClient.getSingleBrokerRequest();
    assertThat(request.getIntent()).isEqualTo(MappingIntent.CREATE);
    assertThat(request.getValueType()).isEqualTo(ValueType.MAPPING);
    final MappingRecord brokerRequestValue = request.getRequestWriter();
    assertThat(brokerRequestValue.getClaimName()).isEqualTo(mappingDTO.claimName());
    assertThat(brokerRequestValue.getClaimValue()).isEqualTo(mappingDTO.claimValue());
    assertThat(brokerRequestValue.getName()).isEqualTo(mappingDTO.name());
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
    final var result = new SearchQueryResult<>(1, List.of(entity), Arrays.array(), Arrays.array());
    when(client.searchMappings(any())).thenReturn(result);
  }

  @Test
  public void shouldReturnSingleVariableForFind() {
    // given
    final var entity = mock(MappingEntity.class);
    final var result = new SearchQueryResult<>(1, List.of(entity), Arrays.array(), Arrays.array());
    when(client.searchMappings(any())).thenReturn(result);

    // when
    final var searchQueryResult = services.findMapping("mappingId");

    // then
    assertThat(searchQueryResult).contains(entity);
  }

  @Test
  public void shouldReturnEmptyWhenNotFoundByFind() {
    // given
    when(client.searchMappings(any())).thenReturn(new SearchQueryResult(0, List.of(), null, null));

    // when / then
    assertThat(services.findMapping("mappingId")).isEmpty();
  }

  @Test
  public void shouldThrowExceptionWhenNotFoundByGet() {
    // given
    when(client.searchMappings(any())).thenReturn(new SearchQueryResult(0, List.of(), null, null));

    // when / then
    final var exception =
        assertThrows(CamundaSearchException.class, () -> services.getMapping("mappingId"));
    assertThat(exception.getReason()).isEqualTo(CamundaSearchException.Reason.NOT_FOUND);
  }

  @Test
  public void shouldTriggerDeleteRequest() {
    // given
    final Authentication testAuthentication = mock(Authentication.class);
    when(testAuthentication.claims()).thenReturn(Map.of());
    final BrokerClient mockBrokerClient = mock(BrokerClient.class);
    final MappingServices testMappingServices =
        new MappingServices(
            mockBrokerClient, mock(SecurityContextProvider.class), client, testAuthentication);

    final var mappingRecord = new MappingRecord();
    mappingRecord.setMappingId("id");
    when(mockBrokerClient.sendRequest(any()))
        .thenReturn(CompletableFuture.completedFuture(new BrokerResponse<>(mappingRecord)));

    //  when
    testMappingServices.deleteMapping("id");

    // then
    verify(mockBrokerClient).sendRequest(mappingDeleteRequestArgumentCaptor.capture());
    final var request = mappingDeleteRequestArgumentCaptor.getValue();
    assertThat(request.getRequestWriter().getMappingId()).isEqualTo("id");
  }

  @Test
  public void shouldTriggerUpdateRequest() {
    // given
    final Authentication testAuthentication = mock(Authentication.class);
    when(testAuthentication.claims()).thenReturn(Map.of());
    final BrokerClient mockBrokerClient = mock(BrokerClient.class);
    final MappingServices testMappingServices =
        new MappingServices(
            mockBrokerClient, mock(SecurityContextProvider.class), client, testAuthentication);

    final var mappingRecord = new MappingRecord();
    mappingRecord.setMappingId("id");
    when(mockBrokerClient.sendRequest(any()))
        .thenReturn(CompletableFuture.completedFuture(new BrokerResponse<>(mappingRecord)));

    final var mappingDTO =
        new MappingDTO(
            "newClaimName", "newClaimValue", "newMappingRuleName", mappingRecord.getMappingId());

    //  when
    testMappingServices.updateMapping(mappingDTO);

    // then
    verify(mockBrokerClient).sendRequest(mappingUpdateRequestArgumentCaptor.capture());
    final var request = mappingUpdateRequestArgumentCaptor.getValue();
    assertThat(request.getRequestWriter().getMappingId()).isEqualTo(mappingDTO.mappingId());
    assertThat(request.getRequestWriter().getClaimName()).isEqualTo(mappingDTO.claimName());
    assertThat(request.getRequestWriter().getClaimValue()).isEqualTo(mappingDTO.claimValue());
    assertThat(request.getRequestWriter().getName()).isEqualTo(mappingDTO.name());
  }

  @Test
  public void getMatchingMappings() {
    // given
    final Map<String, Object> claims =
        Map.of("c1", "v1", "c2", List.of("v2.1", "v2.2"), "c3", 300, "c4", true);
    // when
    services.getMatchingMappings(claims);
    // then
    final ArgumentCaptor<MappingQuery> queryCaptor = ArgumentCaptor.forClass(MappingQuery.class);
    verify(client, times(1)).findAllMappings(queryCaptor.capture());
    final MappingQuery query = queryCaptor.getValue();
    assertThat(query.filter().claimName()).isNull();
    assertThat(query.filter().claimValue()).isNull();
    assertThat(query.filter().name()).isNull();
    assertThat(query.filter().claims())
        .containsExactlyInAnyOrder(
            new Claim("c1", "v1"),
            new Claim("c2", "v2.1"),
            new Claim("c2", "v2.2"),
            new Claim("c3", "300"),
            new Claim("c4", "true"));
  }
}
