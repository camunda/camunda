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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.DecisionInstanceSearchClient;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.query.DecisionInstanceQuery;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DecisionInstanceServiceTest {

  private DecisionInstanceServices services;
  private DecisionInstanceSearchClient client;

  @BeforeEach
  public void before() {
    client = mock(DecisionInstanceSearchClient.class);
    services = new DecisionInstanceServices(mock(BrokerClient.class), client, null);
  }

  @Test
  void shouldReturnDecisionInstances() {
    // given
    final var result = mock(SearchQueryResult.class);
    when(client.searchDecisionInstances(any(), any())).thenReturn(result);

    final DecisionInstanceQuery searchQuery =
        SearchQueryBuilders.decisionInstanceSearchQuery().build();

    // when
    final SearchQueryResult<DecisionInstanceEntity> searchQueryResult =
        services.search(searchQuery);

    // then
    assertThat(searchQueryResult).isEqualTo(result);
  }

  @Test
  void shouldGetDecisionInstanceByKey() {
    // given
    final Long decisionInstanceKey = 1L;
    final var result = mock(SearchQueryResult.class);
    when(result.total()).thenReturn(1L);
    when(result.items()).thenReturn(List.of(mock(DecisionInstanceEntity.class)));
    when(client.searchDecisionInstances(any(), any())).thenReturn(result);

    // when
    services.getByKey(decisionInstanceKey);

    // then
    verify(client)
        .searchDecisionInstances(
            SearchQueryBuilders.decisionInstanceSearchQuery(
                q -> q.filter(f -> f.decisionInstanceKeys(decisionInstanceKey))),
            null);
  }

  @Test
  void shouldSearchDecisionInstances() {
    // given
    final DecisionInstanceQuery query =
        SearchQueryBuilders.decisionInstanceSearchQuery(
            q ->
                q.filter(f -> f.tenantIds("tenant1"))
                    .sort(s -> s.evaluationDate().asc())
                    .page(p -> p.size(20)));

    // when
    services.search(query);

    // then
    verify(client)
        .searchDecisionInstances(
            SearchQueryBuilders.decisionInstanceSearchQuery(
                q ->
                    q.filter(f -> f.tenantIds("tenant1"))
                        .sort(s -> s.evaluationDate().asc())
                        .page(p -> p.size(20))
                        .resultConfig(
                            r -> r.evaluatedInputs().exclude().evaluatedOutputs().exclude())),
            null);
  }
}
