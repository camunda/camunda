/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.core.SearchQueryHit;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.core.SearchQueryResponse;
import io.camunda.search.transformers.ServiceTransformers;
import io.camunda.service.entities.ProcessInstanceEntity;
import io.camunda.service.entities.ProcessInstanceEntity.ProcessInstanceState;
import io.camunda.service.search.query.ProcessInstanceQuery;
import io.camunda.zeebe.util.Either;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SearchClientBasedQueryExecutorTest {

  private final ProcessInstanceEntity demoProcessInstance =
      new ProcessInstanceEntity(
          123L,
          "Demo Process",
          "demoProcess",
          5,
          "42",
          789L,
          777L,
          null,
          null,
          "default",
          "2024-01-01T00:00:00Z",
          null,
          ProcessInstanceState.ACTIVE,
          false,
          null
      );

  @Mock
  private DocumentCamundaSearchClient searchClient;
  private final ServiceTransformers serviceTransformers = ServiceTransformers.newInstance();

  private SearchClientBasedQueryExecutor queryExecutor;

  @BeforeEach
  void setUp() {
    queryExecutor = new SearchClientBasedQueryExecutor(searchClient, serviceTransformers, null);
  }

  @Test
  void shouldSearchUsingTransformers() {
    // Given our search Query
    final var searchAllQuery = new ProcessInstanceQuery.Builder().build();

    // And our search client returns stuff
    final SearchQueryResponse<ProcessInstanceEntity> processInstanceEntityResponse = createProcessInstanceEntityResponse(
        demoProcessInstance);

    when(searchClient.search(any(SearchQueryRequest.class), any(Class.class)))
        .thenReturn(Either.right(processInstanceEntityResponse));

    // When we search
    final var searchResult = queryExecutor.search(searchAllQuery, ProcessInstanceEntity.class);

    assertThat(searchResult.isRight()).isTrue();
    assertThat(searchResult.get().total()).isEqualTo(1);
    final List<ProcessInstanceEntity> items = searchResult.get().items();
    assertThat(items).hasSize(1);
    assertThat(items.getFirst().key()).isEqualTo(demoProcessInstance.key());
  }

  private SearchQueryResponse<ProcessInstanceEntity> createProcessInstanceEntityResponse(
      final ProcessInstanceEntity demoProcessInstance) {
    final SearchQueryHit<ProcessInstanceEntity> hit =
        new SearchQueryHit.Builder<ProcessInstanceEntity>()
            .id("1000")
            .source(demoProcessInstance)
            .build();

    return SearchQueryResponse.of(
        (f) -> {
          f.totalHits(1).hits(List.of(hit));
          return f;
        });
  }
}
