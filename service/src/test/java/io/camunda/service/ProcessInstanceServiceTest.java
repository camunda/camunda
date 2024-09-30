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

import io.camunda.search.clients.ProcessInstanceSearchClient;
import io.camunda.search.entities.DecisionRequirementsEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.exception.NotFoundException;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public final class ProcessInstanceServiceTest {

  private ProcessInstanceServices services;
  private ProcessInstanceSearchClient client;

  @BeforeEach
  public void before() {
    client = mock(ProcessInstanceSearchClient.class);
    services = new ProcessInstanceServices(mock(BrokerClient.class), client, null);
  }

  @Test
  public void shouldReturnProcessInstance() {
    // given
    final var result = mock(SearchQueryResult.class);
    when(client.searchProcessInstances(any(), any())).thenReturn(result);

    final ProcessInstanceQuery searchQuery =
        SearchQueryBuilders.processInstanceSearchQuery().build();

    // when
    final SearchQueryResult<ProcessInstanceEntity> searchQueryResult = services.search(searchQuery);

    // then
    assertThat(searchQueryResult).isEqualTo(result);
  }

  @Test
  public void shouldReturnProcessInstanceByKey() {
    // given
    final var key = 123L;
    final var entity = mock(ProcessInstanceEntity.class);
    when(entity.key()).thenReturn(key);
    when(client.searchProcessInstances(any(), any()))
        .thenReturn(new SearchQueryResult(1, List.of(entity), null));

    // when
    final var searchQueryResult = services.getByKey(key);

    // then
    assertThat(searchQueryResult.key()).isEqualTo(key);
  }

  @Test
  public void shouldThrownExceptionIfNotFoundByKey() {
    // given
    final var key = 100L;
    when(client.searchProcessInstances(any(), any()))
        .thenReturn(new SearchQueryResult(0, List.of(), null));

    // when / then
    final var exception =
        assertThrowsExactly(NotFoundException.class, () -> services.getByKey(key));
    assertThat(exception.getMessage()).isEqualTo("Process Instance with key 100 not found");
  }

  @Test
  public void shouldThrownExceptionIfDuplicateFoundByKey() {
    // given
    final var key = 200L;
    final var entity1 = mock(DecisionRequirementsEntity.class);
    final var entity2 = mock(DecisionRequirementsEntity.class);
    when(client.searchProcessInstances(any(), any()))
        .thenReturn(new SearchQueryResult(2, List.of(entity1, entity2), null));

    // when / then
    final var exception =
        assertThrowsExactly(CamundaSearchException.class, () -> services.getByKey(key));
    assertThat(exception.getMessage())
        .isEqualTo("Found Process Instance with key 200 more than once");
  }
}
