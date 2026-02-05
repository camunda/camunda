/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation.itemprovider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.instancio.Select.field;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.SearchClientsProxy;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.filter.DecisionInstanceFilter;
import io.camunda.search.query.DecisionInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.metrics.BatchOperationMetrics;
import java.util.List;
import org.instancio.Instancio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DecisionInstanceItemProviderTest {

  private SearchClientsProxy searchClientsProxy = mock(SearchClientsProxy.class);
  private final BatchOperationMetrics metrics = mock(BatchOperationMetrics.class);

  private DecisionInstanceItemProvider provider;

  private final CamundaAuthentication authentication = mock(CamundaAuthentication.class);

  @BeforeEach
  void setUp() {
    searchClientsProxy = mock(SearchClientsProxy.class);

    final EngineConfiguration engineConfiguration = mock(EngineConfiguration.class);
    when(engineConfiguration.getBatchOperationQueryPageSize()).thenReturn(5);
    when(engineConfiguration.getBatchOperationQueryInClauseSize()).thenReturn(5);

    when(searchClientsProxy.withSecurityContext(any())).thenReturn(searchClientsProxy);

    provider =
        new DecisionInstanceItemProvider(
            searchClientsProxy,
            metrics,
            new DecisionInstanceFilter.Builder().build(),
            authentication);
  }

  @Test
  public void shouldFetchPage() {
    final var queryCaptor = ArgumentCaptor.forClass(DecisionInstanceQuery.class);

    // given
    final var result =
        new SearchQueryResult.Builder<DecisionInstanceEntity>()
            .items(
                List.of(
                    createDecisionInstanceEntity(1L),
                    createDecisionInstanceEntity(2L),
                    createDecisionInstanceEntity(3L),
                    createDecisionInstanceEntity(4L),
                    createDecisionInstanceEntity(5L)))
            .total(5)
            .endCursor("5")
            .build();
    when(searchClientsProxy.searchDecisionInstances(queryCaptor.capture())).thenReturn(result);

    // when
    final var resultPage = provider.fetchItemPage("-1", 5);

    // then
    assertThat(resultPage.items()).hasSize(5);
    assertThat(resultPage.endCursor()).isEqualTo("5");
    assertThat(resultPage.isLastPage()).isFalse();
    assertThat(resultPage.items().getFirst().itemKey()).isEqualTo(1L);
  }

  @Test
  public void shouldFetchEmptyPage() {
    final var queryCaptor = ArgumentCaptor.forClass(DecisionInstanceQuery.class);

    // given
    final var result =
        new SearchQueryResult.Builder<DecisionInstanceEntity>()
            .items(List.of())
            .total(0)
            .endCursor("0")
            .build();
    when(searchClientsProxy.searchDecisionInstances(queryCaptor.capture())).thenReturn(result);

    // when
    final var resultPage = provider.fetchItemPage("-1", 5);

    // then
    assertThat(resultPage.items()).hasSize(0);
    assertThat(resultPage.endCursor()).isEqualTo("0");
    assertThat(resultPage.isLastPage()).isTrue();
  }

  @Test
  public void shouldFetchSmallerPage() {
    final var queryCaptor = ArgumentCaptor.forClass(DecisionInstanceQuery.class);

    // given
    final var result =
        new SearchQueryResult.Builder<DecisionInstanceEntity>()
            .items(
                List.of(
                    createDecisionInstanceEntity(1L),
                    createDecisionInstanceEntity(2L),
                    createDecisionInstanceEntity(3L)))
            .total(3)
            .endCursor("3")
            .build();
    when(searchClientsProxy.searchDecisionInstances(queryCaptor.capture())).thenReturn(result);

    // when
    final var resultPage = provider.fetchItemPage(null, 5);

    // then
    assertThat(resultPage.items()).hasSize(3);
    assertThat(resultPage.endCursor()).isEqualTo("3");
    assertThat(resultPage.isLastPage()).isTrue();
    assertThat(resultPage.items().get(0).itemKey()).isEqualTo(1L);
    assertThat(resultPage.items().get(1).itemKey()).isEqualTo(2L);
    assertThat(resultPage.items().get(2).itemKey()).isEqualTo(3L);
  }

  private DecisionInstanceEntity createDecisionInstanceEntity(final long decisionInstanceKey) {
    return Instancio.of(DecisionInstanceEntity.class)
        .set(field(DecisionInstanceEntity::decisionInstanceKey), decisionInstanceKey)
        .create();
  }
}
