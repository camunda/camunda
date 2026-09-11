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
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.metrics.BatchOperationMetrics;
import io.camunda.zeebe.engine.processing.batchoperation.itemprovider.ItemProvider.Item;
import java.util.List;
import org.instancio.Instancio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ProcessInstanceItemProviderTest {

  private SearchClientsProxy searchClientsProxy = mock(SearchClientsProxy.class);
  private final BatchOperationMetrics metrics = mock(BatchOperationMetrics.class);

  private ProcessInstanceItemProvider provider;

  private final CamundaAuthentication authentication = mock(CamundaAuthentication.class);

  @BeforeEach
  void setUp() {
    searchClientsProxy = mock(SearchClientsProxy.class);

    final EngineConfiguration engineConfiguration = mock(EngineConfiguration.class);
    when(engineConfiguration.getBatchOperationQueryPageSize()).thenReturn(5);
    when(engineConfiguration.getBatchOperationQueryInClauseSize()).thenReturn(5);

    when(searchClientsProxy.withSecurityContext(any())).thenReturn(searchClientsProxy);

    provider =
        new ProcessInstanceItemProvider(
            searchClientsProxy,
            metrics,
            new ProcessInstanceFilter.Builder().build(),
            authentication);
  }

  @Test
  public void shouldFetchPage() {
    final var queryCaptor = ArgumentCaptor.forClass(ProcessInstanceQuery.class);

    // given
    final var result =
        new SearchQueryResult.Builder<ProcessInstanceEntity>()
            .items(
                List.of(
                    createProcessInstanceEntity(1L, null, 101),
                    createProcessInstanceEntity(2L, 2L, 102),
                    createProcessInstanceEntity(3L, 2L, 103),
                    createProcessInstanceEntity(4L, 4L, 104),
                    createProcessInstanceEntity(5L, 4L, 105)))
            .total(5)
            .endCursor("5")
            .build();
    when(searchClientsProxy.searchProcessInstances(queryCaptor.capture())).thenReturn(result);

    // when
    final var resultPage = provider.fetchItemPage("-1", 5);

    // then
    assertThat(resultPage.endCursor()).isEqualTo("5");
    assertThat(resultPage.isLastPage()).isFalse();
    assertThat(resultPage.items())
        .isEqualTo(
            List.of(
                new Item(1L, 1L, null, 101),
                new Item(2L, 2L, 2L, 102),
                new Item(3L, 3L, 2L, 103),
                new Item(4L, 4L, 4L, 104),
                new Item(5L, 5L, 4L, 105)));
  }

  @Test
  public void shouldFetchEmptyPage() {
    final var queryCaptor = ArgumentCaptor.forClass(ProcessInstanceQuery.class);

    // given
    final var result =
        new SearchQueryResult.Builder<ProcessInstanceEntity>()
            .items(List.of())
            .total(0)
            .endCursor("0")
            .build();
    when(searchClientsProxy.searchProcessInstances(queryCaptor.capture())).thenReturn(result);

    // when
    final var resultPage = provider.fetchItemPage("-1", 5);

    // then
    assertThat(resultPage.items()).hasSize(0);
    assertThat(resultPage.endCursor()).isEqualTo("0");
    assertThat(resultPage.isLastPage()).isTrue();
  }

  @Test
  public void shouldFetchSmallerPage() {
    final var queryCaptor = ArgumentCaptor.forClass(ProcessInstanceQuery.class);

    // given
    final var result =
        new SearchQueryResult.Builder<ProcessInstanceEntity>()
            .items(
                List.of(
                    createProcessInstanceEntity(1L, null, 201),
                    createProcessInstanceEntity(2L, 2L, 202),
                    createProcessInstanceEntity(3L, 3L, 203)))
            .total(3)
            .endCursor("3")
            .build();
    when(searchClientsProxy.searchProcessInstances(queryCaptor.capture())).thenReturn(result);

    // when
    final var resultPage = provider.fetchItemPage(null, 5);

    // then
    assertThat(resultPage.endCursor()).isEqualTo("3");
    assertThat(resultPage.isLastPage()).isTrue();
    assertThat(resultPage.items())
        .isEqualTo(
            List.of(
                new Item(1L, 1L, null, 201), new Item(2L, 2L, 2L, 202), new Item(3L, 3L, 3L, 203)));
  }

  private ProcessInstanceEntity createProcessInstanceEntity(
      final long processInstanceKey,
      final Long rootProcessInstanceKey,
      final Integer storageOrdinalKey) {
    return Instancio.of(ProcessInstanceEntity.class)
        .set(field(ProcessInstanceEntity::processInstanceKey), processInstanceKey)
        .set(field(ProcessInstanceEntity::rootProcessInstanceKey), rootProcessInstanceKey)
        .set(field(ProcessInstanceEntity::storageOrdinalKey), storageOrdinalKey)
        .create();
  }
}
