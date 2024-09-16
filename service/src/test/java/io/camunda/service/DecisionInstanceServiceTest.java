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
import static org.mockito.Mockito.when;

import io.camunda.search.clients.DecisionInstanceSearchClient;
import io.camunda.service.entities.DecisionInstanceEntity;
import io.camunda.service.search.query.DecisionInstanceQuery;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.util.Either;
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
    when(client.searchDecisionInstances(any(), any())).thenReturn(Either.right(result));

    final DecisionInstanceQuery searchQuery =
        SearchQueryBuilders.decisionInstanceSearchQuery().build();

    // when
    final SearchQueryResult<DecisionInstanceEntity> searchQueryResult =
        services.search(searchQuery);

    // then
    assertThat(searchQueryResult).isEqualTo(result);
  }
}
