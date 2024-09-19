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

import io.camunda.search.clients.FlowNodeInstanceSearchClient;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class FlowNodeInstanceServiceTest {

  private FlowNodeInstanceServices services;
  private FlowNodeInstanceSearchClient client;

  @BeforeEach
  public void before() {
    client = mock(FlowNodeInstanceSearchClient.class);
    services = new FlowNodeInstanceServices(mock(BrokerClient.class), client, null);
  }

  @Test
  public void shouldReturnFlowNodeInstance() {
    // given
    final var result = mock(SearchQueryResult.class);
    when(client.searchFlowNodeInstances(any(), any())).thenReturn(result);

    final var searchQuery = SearchQueryBuilders.flownodeInstanceSearchQuery().build();

    // when
    final var searchQueryResult = services.search(searchQuery);

    // then
    assertThat(result).isEqualTo(searchQueryResult);
  }
}
