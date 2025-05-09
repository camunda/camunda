/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.batchoperation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.SearchClientsProxy;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.zeebe.engine.processing.batchoperation.BatchOperationItemProvider.Item;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class BatchOperationItemProviderTest {

  private static final int PARTITION_ID = 1;

  private final SearchClientsProxy searchClientsProxy = mock(SearchClientsProxy.class);

  private final BatchOperationItemProvider provider =
      new BatchOperationItemProvider(searchClientsProxy);

  @Test
  public void shouldFetchProcessInstanceKeys() {
    final var queryCaptor = ArgumentCaptor.forClass(ProcessInstanceQuery.class);

    // given
    final var result =
        new SearchQueryResult.Builder<ProcessInstanceEntity>()
            .items(
                List.of(
                    mockProcessInstanceEntity(1L),
                    mockProcessInstanceEntity(2L),
                    mockProcessInstanceEntity(3L)))
            .total(3)
            .build();
    when(searchClientsProxy.searchProcessInstances(queryCaptor.capture())).thenReturn(result);

    // when
    final var filter = new ProcessInstanceFilter.Builder().build();
    final var resultKeys = provider.fetchProcessInstanceItems(PARTITION_ID, filter, () -> false);

    // then
    assertThat(resultKeys).containsExactly(new Item(1L, 1L), new Item(2L, 2L), new Item(3L, 3L));

    // and partitionId is set in filter
    final var query = queryCaptor.getValue();
    assertThat(query.filter().partitionId()).isEqualTo(PARTITION_ID);
  }

  @Test
  public void shouldFetchProcessInstanceKeysInMultiplePages() {
    final var queryCaptor = ArgumentCaptor.forClass(ProcessInstanceQuery.class);

    // given
    final var result =
        new SearchQueryResult.Builder<ProcessInstanceEntity>()
            .items(
                List.of(
                    mockProcessInstanceEntity(1L),
                    mockProcessInstanceEntity(2L),
                    mockProcessInstanceEntity(3L)))
            .total(6)
            .build();
    final var result2 =
        new SearchQueryResult.Builder<ProcessInstanceEntity>()
            .items(
                List.of(
                    mockProcessInstanceEntity(4L),
                    mockProcessInstanceEntity(5L),
                    mockProcessInstanceEntity(6L)))
            .total(6)
            .build();
    when(searchClientsProxy.searchProcessInstances(queryCaptor.capture()))
        .thenReturn(result)
        .thenReturn(result2);

    // when
    final var filter = new ProcessInstanceFilter.Builder().build();
    final var resultKeys = provider.fetchProcessInstanceItems(PARTITION_ID, filter, () -> false);

    // then
    assertThat(resultKeys)
        .containsExactly(
            new Item(1L, 1L),
            new Item(2L, 2L),
            new Item(3L, 3L),
            new Item(4L, 4L),
            new Item(5L, 5L),
            new Item(6L, 6L));

    // and partitionId is set in filter
    final var query = queryCaptor.getValue();
    assertThat(query.filter().partitionId()).isEqualTo(PARTITION_ID);
  }

  @Test
  public void shouldFetchProcessInstanceKeysWithEmptyPage() {
    final var queryCaptor = ArgumentCaptor.forClass(ProcessInstanceQuery.class);

    // given
    final var result =
        new SearchQueryResult.Builder<ProcessInstanceEntity>()
            .items(
                List.of(
                    mockProcessInstanceEntity(1L),
                    mockProcessInstanceEntity(2L),
                    mockProcessInstanceEntity(3L)))
            .total(6)
            .build();
    final var result2 =
        new SearchQueryResult.Builder<ProcessInstanceEntity>().items(List.of()).total(6).build();
    when(searchClientsProxy.searchProcessInstances(queryCaptor.capture()))
        .thenReturn(result)
        .thenReturn(result2);

    // when
    final var filter = new ProcessInstanceFilter.Builder().build();
    final var resultKeys = provider.fetchProcessInstanceItems(PARTITION_ID, filter, () -> false);

    // then
    assertThat(resultKeys).containsExactly(new Item(1L, 1L), new Item(2L, 2L), new Item(3L, 3L));

    // and partitionId is set in filter
    final var query = queryCaptor.getValue();
    assertThat(query.filter().partitionId()).isEqualTo(PARTITION_ID);
  }

  @Test
  public void shouldReturnEmptyListWhenAborted() {
    final var queryCaptor = ArgumentCaptor.forClass(ProcessInstanceQuery.class);
    final AtomicBoolean shouldAbort = new AtomicBoolean(false);

    // given
    final var result =
        new SearchQueryResult.Builder<ProcessInstanceEntity>()
            .items(
                List.of(
                    mockProcessInstanceEntity(1L),
                    mockProcessInstanceEntity(2L),
                    mockProcessInstanceEntity(3L)))
            .total(6)
            .build();
    final var result2 =
        new SearchQueryResult.Builder<ProcessInstanceEntity>()
            .items(
                List.of(
                    mockProcessInstanceEntity(4L),
                    mockProcessInstanceEntity(5L),
                    mockProcessInstanceEntity(6L)))
            .total(6)
            .build();
    when(searchClientsProxy.searchProcessInstances(queryCaptor.capture()))
        .then(
            (i) -> {
              shouldAbort.set(true);
              return result;
            })
        .thenReturn(result2);

    // when
    final var filter = new ProcessInstanceFilter.Builder().build();
    final var resultItems =
        provider.fetchProcessInstanceItems(PARTITION_ID, filter, shouldAbort::get);

    // then
    assertThat(resultItems).isEmpty();

    // and partitionId is set in filter
    final var query = queryCaptor.getValue();
    assertThat(query.filter().partitionId()).isEqualTo(PARTITION_ID);
  }

  @Test
  public void shouldFetchIncidentItems() {
    final var queryCaptor = ArgumentCaptor.forClass(ProcessInstanceQuery.class);

    // given
    final var processInstanceResult =
        new SearchQueryResult.Builder<ProcessInstanceEntity>()
            .items(
                List.of(
                    mockProcessInstanceEntity(1L),
                    mockProcessInstanceEntity(2L),
                    mockProcessInstanceEntity(3L)))
            .total(3)
            .build();
    when(searchClientsProxy.searchProcessInstances(queryCaptor.capture()))
        .thenReturn(processInstanceResult);

    // given
    final var incidentResult =
        new SearchQueryResult.Builder<IncidentEntity>()
            .items(
                List.of(
                    mockIncidentEntity(11L, 1L),
                    mockIncidentEntity(12L, 2L),
                    mockIncidentEntity(13L, 3L)))
            .total(3)
            .build();
    when(searchClientsProxy.searchIncidents(any())).thenReturn(incidentResult);

    // when
    final var filter = new ProcessInstanceFilter.Builder().build();
    final var resultKeys = provider.fetchIncidentItems(PARTITION_ID, filter, () -> false);

    // then
    assertThat(resultKeys).containsExactly(new Item(11L, 1L), new Item(12L, 2L), new Item(13L, 3L));

    // and partitionId is set in process instance filter
    final var query = queryCaptor.getValue();
    assertThat(query.filter().partitionId()).isEqualTo(PARTITION_ID);
  }

  private ProcessInstanceEntity mockProcessInstanceEntity(final long processInstanceKey) {
    final var entity = mock(ProcessInstanceEntity.class);
    when(entity.processInstanceKey()).thenReturn(processInstanceKey);
    return entity;
  }

  private IncidentEntity mockIncidentEntity(final long incidentKey, final long processInstanceKey) {
    final var entity = mock(IncidentEntity.class);
    when(entity.incidentKey()).thenReturn(incidentKey);
    when(entity.processInstanceKey()).thenReturn(processInstanceKey);
    return entity;
  }
}
