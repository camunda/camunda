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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.SearchClientsProxy;
import io.camunda.search.entities.IncidentEntity;
import io.camunda.search.entities.IncidentEntity.IncidentState;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.filter.IncidentFilter;
import io.camunda.search.filter.ProcessInstanceFilter;
import io.camunda.search.query.IncidentQuery;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.util.FilterUtil;
import io.camunda.zeebe.engine.EngineConfiguration;
import io.camunda.zeebe.engine.metrics.BatchOperationMetrics;
import io.camunda.zeebe.engine.processing.batchoperation.itemprovider.ItemProvider.Item;
import java.util.List;
import org.instancio.Instancio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class IncidentItemProviderTest {

  private SearchClientsProxy searchClientsProxy = mock(SearchClientsProxy.class);
  private final BatchOperationMetrics metrics = mock(BatchOperationMetrics.class);

  private IncidentItemProvider provider;

  private final CamundaAuthentication authentication = mock(CamundaAuthentication.class);
  private final ProcessInstanceFilter filter =
      new ProcessInstanceFilter.Builder().processDefinitionIds("test-process").build();

  @BeforeEach
  void setUp() {
    searchClientsProxy = mock(SearchClientsProxy.class);

    final EngineConfiguration engineConfiguration = mock(EngineConfiguration.class);
    when(engineConfiguration.getBatchOperationQueryPageSize()).thenReturn(5);
    when(engineConfiguration.getBatchOperationQueryInClauseSize()).thenReturn(5);

    when(searchClientsProxy.withSecurityContext(any())).thenReturn(searchClientsProxy);

    provider = new IncidentItemProvider(searchClientsProxy, metrics, filter, authentication);
  }

  @Test
  public void shouldFetchIncidentKeysInMultiplePages() {
    final var piQueryCaptor = ArgumentCaptor.forClass(ProcessInstanceQuery.class);
    final var incidentQueryCaptor = ArgumentCaptor.forClass(IncidentQuery.class);

    // given
    final var piResult =
        new SearchQueryResult.Builder<ProcessInstanceEntity>()
            .items(
                List.of(
                    createProcessInstanceEntity(1L, 1L),
                    createProcessInstanceEntity(2L, 1L),
                    createProcessInstanceEntity(3L, 3L),
                    createProcessInstanceEntity(4L, null),
                    createProcessInstanceEntity(5L, null)))
            .total(5)
            .build();
    when(searchClientsProxy.searchProcessInstances(any())).thenReturn(piResult);

    final var incidentResult1 =
        new SearchQueryResult.Builder<IncidentEntity>()
            .items(
                List.of(
                    mockIncidentEntity(11, 1, 1L, null),
                    mockIncidentEntity(12, 1, 1L, null),
                    mockIncidentEntity(21, 2, 1L, null),
                    mockIncidentEntity(22, 2, 1L, null),
                    mockIncidentEntity(31, 3, 3L, 123)))
            .total(10)
            .endCursor("cursor-1")
            .build();
    final var incidentResult2 =
        new SearchQueryResult.Builder<IncidentEntity>()
            .items(
                List.of(
                    mockIncidentEntity(32, 3, 3L, 123),
                    mockIncidentEntity(41, 4, null, 456),
                    mockIncidentEntity(42, 4, null, 456),
                    mockIncidentEntity(51, 5, null, 456),
                    mockIncidentEntity(52, 5, null, 456)))
            .total(10)
            .endCursor("cursor-2")
            .build();
    final var incidentResult3 =
        new SearchQueryResult.Builder<IncidentEntity>().items(List.of()).total(10).build();

    when(searchClientsProxy.searchIncidents(any()))
        .thenReturn(incidentResult1)
        .thenReturn(incidentResult2)
        .thenReturn(incidentResult3);

    // when
    final var resultPage = provider.fetchItemPage("-1", 5);

    // then
    verify(searchClientsProxy).searchProcessInstances(piQueryCaptor.capture());
    assertProcessInstancesSearchedWith(piQueryCaptor.getValue(), "-1", 5);

    verify(searchClientsProxy, times(3)).searchIncidents(incidentQueryCaptor.capture());
    final var expectedIncidentFilter = activeIncidentsOf(List.of(1L, 2L, 3L, 4L, 5L));
    assertThat(incidentQueryCaptor.getAllValues())
        .extracting(IncidentQuery::filter)
        .containsOnly(expectedIncidentFilter);
    assertThat(incidentQueryCaptor.getAllValues())
        .extracting(q -> q.page().after())
        .containsExactly(null, "cursor-1", "cursor-2");
    assertThat(incidentQueryCaptor.getAllValues()).extracting(q -> q.page().size()).containsOnly(5);

    assertThat(resultPage.items())
        .containsExactly(
            new Item(11, 1, 1L, null),
            new Item(12, 1, 1L, null),
            new Item(21, 2, 1L, null),
            new Item(22, 2, 1L, null),
            new Item(31, 3, 3L, 123),
            new Item(32, 3, 3L, 123),
            new Item(41, 4, null, 456),
            new Item(42, 4, null, 456),
            new Item(51, 5, null, 456),
            new Item(52, 5, null, 456));
    assertThat(resultPage.isLastPage()).isFalse();
  }

  @Test
  public void shouldFetchProcessInstanceKeysWithEmptyIncidentPage() {
    final var piQueryCaptor = ArgumentCaptor.forClass(ProcessInstanceQuery.class);
    final var incidentQueryCaptor = ArgumentCaptor.forClass(IncidentQuery.class);

    // given
    final var piResult =
        new SearchQueryResult.Builder<ProcessInstanceEntity>()
            .items(
                List.of(
                    createProcessInstanceEntity(1L, 1L),
                    createProcessInstanceEntity(2L, 2L),
                    createProcessInstanceEntity(3L, 1L),
                    createProcessInstanceEntity(4L, 2L),
                    createProcessInstanceEntity(5L, null)))
            .total(5)
            .build();
    when(searchClientsProxy.searchProcessInstances(any())).thenReturn(piResult);

    final var incidentResult =
        new SearchQueryResult.Builder<IncidentEntity>().items(List.of()).total(0).build();

    when(searchClientsProxy.searchIncidents(any())).thenReturn(incidentResult);

    // when
    final var resultPage = provider.fetchItemPage("-1", 5);

    // then
    verify(searchClientsProxy).searchProcessInstances(piQueryCaptor.capture());
    assertProcessInstancesSearchedWith(piQueryCaptor.getValue(), "-1", 5);

    verify(searchClientsProxy).searchIncidents(incidentQueryCaptor.capture());
    assertThat(incidentQueryCaptor.getValue().filter())
        .isEqualTo(activeIncidentsOf(List.of(1L, 2L, 3L, 4L, 5L)));
    assertThat(incidentQueryCaptor.getValue().page().after()).isNull();
    assertThat(incidentQueryCaptor.getValue().page().size()).isEqualTo(5);

    assertThat(resultPage.items()).isEmpty();
    assertThat(resultPage.isLastPage()).isFalse(); // because processInstances are still 5
  }

  @Test
  public void shouldFetchIncidentKeysWithEmptyProcessIncidentPage() {
    final var piQueryCaptor = ArgumentCaptor.forClass(ProcessInstanceQuery.class);

    // given
    final var piResult =
        new SearchQueryResult.Builder<ProcessInstanceEntity>().items(List.of()).total(0).build();
    when(searchClientsProxy.searchProcessInstances(any())).thenReturn(piResult);

    // when
    final var resultPage = provider.fetchItemPage("-1", 5);

    // then
    verify(searchClientsProxy).searchProcessInstances(piQueryCaptor.capture());
    assertProcessInstancesSearchedWith(piQueryCaptor.getValue(), "-1", 5);
    verify(searchClientsProxy, never()).searchIncidents(any());

    assertThat(resultPage.items()).isEmpty();
    assertThat(resultPage.isLastPage()).isTrue();
  }

  private void assertProcessInstancesSearchedWith(
      final ProcessInstanceQuery query, final String expectedCursor, final int expectedPageSize) {
    assertThat(query.filter()).isEqualTo(filter);
    assertThat(query.page().after()).isEqualTo(expectedCursor);
    assertThat(query.page().size()).isEqualTo(expectedPageSize);
    assertThat(query.resultConfig().onlyKeys()).isTrue();
  }

  private IncidentFilter activeIncidentsOf(final List<Long> processInstanceKeys) {
    return new IncidentFilter.Builder()
        .processInstanceKeyOperations(FilterUtil.mapDefaultToOperation(processInstanceKeys))
        .states(IncidentState.ACTIVE.name())
        .build();
  }

  private ProcessInstanceEntity createProcessInstanceEntity(
      final long processInstanceKey, final Long rootProcessInstanceKey) {
    return Instancio.of(ProcessInstanceEntity.class)
        .set(field(ProcessInstanceEntity::processInstanceKey), processInstanceKey)
        .set(field(ProcessInstanceEntity::rootProcessInstanceKey), rootProcessInstanceKey)
        .create();
  }

  private IncidentEntity mockIncidentEntity(
      final long incidentKey,
      final long processInstanceKey,
      final Long rootProcessInstanceKey,
      final Integer storageOrdinal) {
    final var entity = mock(IncidentEntity.class);
    when(entity.incidentKey()).thenReturn(incidentKey);
    when(entity.processInstanceKey()).thenReturn(processInstanceKey);
    when(entity.rootProcessInstanceKey()).thenReturn(rootProcessInstanceKey);
    when(entity.storageOrdinal()).thenReturn(storageOrdinal);
    return entity;
  }
}
