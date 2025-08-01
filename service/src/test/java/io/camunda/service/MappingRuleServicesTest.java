/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.MappingRuleSearchClient;
import io.camunda.search.entities.MappingRuleEntity;
import io.camunda.search.filter.MappingRuleFilter;
import io.camunda.search.query.MappingRuleQuery;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.MappingRuleServices.MappingRuleDTO;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.api.util.StubbedBrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerMappingRuleCreateRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerMappingRuleDeleteRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerMappingRuleUpdateRequest;
import io.camunda.zeebe.protocol.impl.record.value.authorization.MappingRuleRecord;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.MappingRuleIntent;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

public class MappingRuleServicesTest {

  ArgumentCaptor<BrokerMappingRuleDeleteRequest> mappingRuleDeleteRequestArgumentCaptor;
  ArgumentCaptor<BrokerMappingRuleUpdateRequest> mappingRuleUpdateRequestArgumentCaptor;
  private MappingRuleServices services;
  private MappingRuleSearchClient client;
  private CamundaAuthentication authentication;
  private StubbedBrokerClient stubbedBrokerClient;
  private SearchQueryResult<MappingRuleEntity> result;

  @BeforeEach
  public void before() {
    authentication = CamundaAuthentication.of(builder -> builder.user("foo"));
    stubbedBrokerClient = new StubbedBrokerClient();
    client = mock(MappingRuleSearchClient.class);
    result = mock(SearchQueryResult.class);
    when(client.withSecurityContext(any())).thenReturn(client);
    when(client.searchMappingRules(any())).thenReturn(result);
    mappingRuleDeleteRequestArgumentCaptor =
        ArgumentCaptor.forClass(BrokerMappingRuleDeleteRequest.class);
    mappingRuleUpdateRequestArgumentCaptor =
        ArgumentCaptor.forClass(BrokerMappingRuleUpdateRequest.class);
    services =
        new MappingRuleServices(
            stubbedBrokerClient, mock(SecurityContextProvider.class), client, authentication);
  }

  @Test
  public void shouldCreateMappingRule() {
    // given
    final var mappingRuleDTO =
        new MappingRuleDTO("newClaimName", "newClaimValue", "mappingRuleName", "mappingRuleId");

    // when
    services.createMappingRule(mappingRuleDTO);

    // then
    final BrokerMappingRuleCreateRequest request = stubbedBrokerClient.getSingleBrokerRequest();
    assertThat(request.getIntent()).isEqualTo(MappingRuleIntent.CREATE);
    assertThat(request.getValueType()).isEqualTo(ValueType.MAPPING_RULE);
    final MappingRuleRecord brokerRequestValue = request.getRequestWriter();
    assertThat(brokerRequestValue.getClaimName()).isEqualTo(mappingRuleDTO.claimName());
    assertThat(brokerRequestValue.getClaimValue()).isEqualTo(mappingRuleDTO.claimValue());
    assertThat(brokerRequestValue.getName()).isEqualTo(mappingRuleDTO.name());
  }

  @Test
  public void shouldEmptyQueryReturnRoles() {
    // given
    final var result = mock(SearchQueryResult.class);
    when(client.searchMappingRules(any())).thenReturn(result);

    final MappingRuleFilter filter = new MappingRuleFilter.Builder().build();
    final var searchQuery = SearchQueryBuilders.mappingRuleSearchQuery((b) -> b.filter(filter));

    // when
    final var searchQueryResult = services.search(searchQuery);

    // then
    assertThat(searchQueryResult).isEqualTo(result);
  }

  @Test
  public void shouldReturnSingleVariable() {
    // given
    final var entity = mock(MappingRuleEntity.class);
    final var result = new SearchQueryResult<>(1, false, List.of(entity), null, null);
    when(client.searchMappingRules(any())).thenReturn(result);
  }

  @Test
  public void shouldReturnSingleMappingRuleForFind() {
    // given
    final var entity = mock(MappingRuleEntity.class);
    when(client.getMappingRule(any(String.class))).thenReturn(entity);

    // when
    final var searchQueryResult = services.getMappingRule("mappingRuleId");

    // then
    assertThat(searchQueryResult).isEqualTo(entity);
  }

  @Test
  public void shouldTriggerDeleteRequest() {
    // given
    final CamundaAuthentication testAuthentication = mock(CamundaAuthentication.class);
    when(testAuthentication.claims()).thenReturn(Map.of());
    final BrokerClient mockBrokerClient = mock(BrokerClient.class);
    final MappingRuleServices testMappingRuleServices =
        new MappingRuleServices(
            mockBrokerClient, mock(SecurityContextProvider.class), client, testAuthentication);

    final var mappingRuleRecord = new MappingRuleRecord();
    mappingRuleRecord.setMappingRuleId("id");
    when(mockBrokerClient.sendRequest(any()))
        .thenReturn(CompletableFuture.completedFuture(new BrokerResponse<>(mappingRuleRecord)));

    //  when
    testMappingRuleServices.deleteMappingRule("id");

    // then
    verify(mockBrokerClient).sendRequest(mappingRuleDeleteRequestArgumentCaptor.capture());
    final var request = mappingRuleDeleteRequestArgumentCaptor.getValue();
    assertThat(request.getRequestWriter().getMappingRuleId()).isEqualTo("id");
  }

  @Test
  public void shouldTriggerUpdateRequest() {
    // given
    final CamundaAuthentication testAuthentication = mock(CamundaAuthentication.class);
    when(testAuthentication.claims()).thenReturn(Map.of());
    final BrokerClient mockBrokerClient = mock(BrokerClient.class);
    final MappingRuleServices testMappingRuleServices =
        new MappingRuleServices(
            mockBrokerClient, mock(SecurityContextProvider.class), client, testAuthentication);

    final var mappingRuleRecord = new MappingRuleRecord();
    mappingRuleRecord.setMappingRuleId("id");
    when(mockBrokerClient.sendRequest(any()))
        .thenReturn(CompletableFuture.completedFuture(new BrokerResponse<>(mappingRuleRecord)));

    final var mappingRuleDTO =
        new MappingRuleDTO(
            "newClaimName",
            "newClaimValue",
            "newMappingRuleName",
            mappingRuleRecord.getMappingRuleId());

    //  when
    testMappingRuleServices.updateMappingRule(mappingRuleDTO);

    // then
    verify(mockBrokerClient).sendRequest(mappingRuleUpdateRequestArgumentCaptor.capture());
    final var request = mappingRuleUpdateRequestArgumentCaptor.getValue();
    assertThat(request.getRequestWriter().getMappingRuleId())
        .isEqualTo(mappingRuleDTO.mappingRuleId());
    assertThat(request.getRequestWriter().getClaimName()).isEqualTo(mappingRuleDTO.claimName());
    assertThat(request.getRequestWriter().getClaimValue()).isEqualTo(mappingRuleDTO.claimValue());
    assertThat(request.getRequestWriter().getName()).isEqualTo(mappingRuleDTO.name());
  }

  @Test
  public void getMatchingMappingRules() {
    // given
    final Map<String, Object> claims =
        Map.of("c1", "v1", "c2", List.of("v2.1", "v2.2"), "c3", 300, "c4", true);
    // when
    services.getMatchingMappingRules(claims);
    // then
    final ArgumentCaptor<MappingRuleQuery> queryCaptor =
        ArgumentCaptor.forClass(MappingRuleQuery.class);
    verify(client, times(1)).searchMappingRules(queryCaptor.capture());
    final MappingRuleQuery query = queryCaptor.getValue();
    assertThat(query.filter().claimName()).isNull();
    assertThat(query.filter().claimValue()).isNull();
    assertThat(query.filter().name()).isNull();
    assertThat(query.filter().claims()).isNull();
  }
}
