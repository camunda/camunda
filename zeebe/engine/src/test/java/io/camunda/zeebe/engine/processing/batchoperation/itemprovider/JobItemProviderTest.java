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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.SearchClientsProxy;
import io.camunda.search.entities.JobEntity;
import io.camunda.search.filter.JobFilter;
import io.camunda.search.query.JobQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.api.model.CamundaAuthentication;
import io.camunda.zeebe.engine.metrics.BatchOperationMetrics;
import io.camunda.zeebe.engine.processing.batchoperation.itemprovider.ItemProvider.Item;
import java.util.List;
import org.instancio.Instancio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class JobItemProviderTest {

  private final SearchClientsProxy searchClientsProxy = mock(SearchClientsProxy.class);
  private final BatchOperationMetrics metrics = mock(BatchOperationMetrics.class);

  private JobItemProvider provider;

  private final CamundaAuthentication authentication = mock(CamundaAuthentication.class);
  private final JobFilter filter = new JobFilter.Builder().types("test-job-type").build();

  @BeforeEach
  void setUp() {
    when(searchClientsProxy.withSecurityContext(any())).thenReturn(searchClientsProxy);

    provider = new JobItemProvider(searchClientsProxy, metrics, filter, authentication);
  }

  @Test
  public void shouldFetchPage() {
    final var queryCaptor = ArgumentCaptor.forClass(JobQuery.class);

    // given
    final var result =
        new SearchQueryResult.Builder<JobEntity>()
            .items(
                List.of(
                    createJobEntity(1L, 10L, null, null),
                    createJobEntity(2L, 20L, 20L, null),
                    createJobEntity(3L, 30L, 20L, 123),
                    createJobEntity(4L, 40L, 40L, 456),
                    createJobEntity(5L, 50L, 50L, 456)))
            .total(5)
            .endCursor("5")
            .build();
    when(searchClientsProxy.searchJobs(any())).thenReturn(result);

    // when
    final var resultPage = provider.fetchItemPage("-1", 5);

    // then
    verify(searchClientsProxy).searchJobs(queryCaptor.capture());
    assertSearchedWith(queryCaptor.getValue(), "-1", 5);
    assertThat(resultPage.endCursor()).isEqualTo("5");
    assertThat(resultPage.isLastPage()).isFalse();
    assertThat(resultPage.items())
        .isEqualTo(
            List.of(
                new Item(1L, 10L, null, null),
                new Item(2L, 20L, 20L, null),
                new Item(3L, 30L, 20L, 123),
                new Item(4L, 40L, 40L, 456),
                new Item(5L, 50L, 50L, 456)));
  }

  @Test
  public void shouldFetchEmptyPage() {
    final var queryCaptor = ArgumentCaptor.forClass(JobQuery.class);

    // given
    final var result =
        new SearchQueryResult.Builder<JobEntity>().items(List.of()).total(0).endCursor("0").build();
    when(searchClientsProxy.searchJobs(any())).thenReturn(result);

    // when
    final var resultPage = provider.fetchItemPage("-1", 5);

    // then
    verify(searchClientsProxy).searchJobs(queryCaptor.capture());
    assertSearchedWith(queryCaptor.getValue(), "-1", 5);
    assertThat(resultPage.items()).hasSize(0);
    assertThat(resultPage.endCursor()).isEqualTo("0");
    assertThat(resultPage.isLastPage()).isTrue();
  }

  @Test
  public void shouldFetchSmallerPage() {
    final var queryCaptor = ArgumentCaptor.forClass(JobQuery.class);

    // given
    final var result =
        new SearchQueryResult.Builder<JobEntity>()
            .items(
                List.of(
                    createJobEntity(1L, 10L, null, null),
                    createJobEntity(2L, 20L, null, 123),
                    createJobEntity(3L, 30L, null, 456)))
            .total(3)
            .endCursor("3")
            .build();
    when(searchClientsProxy.searchJobs(any())).thenReturn(result);

    // when
    final var resultPage = provider.fetchItemPage(null, 5);

    // then
    verify(searchClientsProxy).searchJobs(queryCaptor.capture());
    assertSearchedWith(queryCaptor.getValue(), null, 5);
    assertThat(resultPage.endCursor()).isEqualTo("3");
    assertThat(resultPage.isLastPage()).isTrue();
    assertThat(resultPage.items())
        .isEqualTo(
            List.of(
                new Item(1L, 10L, null, null),
                new Item(2L, 20L, null, 123),
                new Item(3L, 30L, null, 456)));
  }

  private void assertSearchedWith(
      final JobQuery query, final String expectedCursor, final int expectedPageSize) {
    assertThat(query.filter()).isEqualTo(filter);
    assertThat(query.page().after()).isEqualTo(expectedCursor);
    assertThat(query.page().size()).isEqualTo(expectedPageSize);
  }

  private JobEntity createJobEntity(
      final long jobKey,
      final long processInstanceKey,
      final Long rootProcessInstanceKey,
      final Integer storageOrdinal) {
    return Instancio.of(JobEntity.class)
        .set(field(JobEntity::jobKey), jobKey)
        .set(field(JobEntity::processInstanceKey), processInstanceKey)
        .set(field(JobEntity::rootProcessInstanceKey), rootProcessInstanceKey)
        .set(field(JobEntity::storageOrdinal), storageOrdinal)
        .create();
  }
}
