/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.auth.AuthorizationQueryStrategy;
import io.camunda.search.clients.core.SearchQueryHit;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.core.SearchQueryResponse;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.entities.ProcessInstanceEntity.ProcessInstanceState;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.SecurityContext;
import java.time.OffsetDateTime;
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
          null,
          null,
          "default",
          OffsetDateTime.parse("2024-01-01T00:00:00Z"),
          null,
          ProcessInstanceState.ACTIVE,
          false,
          null);

  @Mock private DocumentBasedSearchClient searchClient;
  @Mock private AuthorizationQueryStrategy authorizationQueryStrategy;
  private final ServiceTransformers serviceTransformers =
      ServiceTransformers.newInstance(false, null);

  private SearchClientBasedQueryExecutor queryExecutor;

  @BeforeEach
  void setUp() {
    queryExecutor =
        new SearchClientBasedQueryExecutor(
            searchClient,
            serviceTransformers,
            authorizationQueryStrategy,
            SecurityContext.withoutAuthentication());
  }

  @Test
  void shouldSearchUsingTransformers() {
    // Given our search Query
    final var searchAllQuery = new ProcessInstanceQuery.Builder().build();

    // And our search client returns stuff
    final SearchQueryResponse<ProcessInstanceEntity> processInstanceEntityResponse =
        createProcessInstanceEntityResponse(demoProcessInstance);

    when(searchClient.search(any(SearchQueryRequest.class), eq(ProcessInstanceEntity.class)))
        .thenReturn(processInstanceEntityResponse);
    when(authorizationQueryStrategy.applyAuthorizationToQuery(
            any(SearchQueryRequest.class), any(SecurityContext.class), any()))
        .thenAnswer(i -> i.getArgument(0));

    // When we search
    final SearchQueryResult<ProcessInstanceEntity> searchResult =
        queryExecutor.search(searchAllQuery, ProcessInstanceEntity.class);

    assertThat(searchResult.total()).isEqualTo(1);
    final List<ProcessInstanceEntity> items = searchResult.items();
    assertThat(items).hasSize(1);
    assertThat(items.getFirst().processInstanceKey())
        .isEqualTo(demoProcessInstance.processInstanceKey());
  }

  @Test
  void shouldFindAllUsingTransformers() {
    // Given our search Query
    final var searchAllQuery = new ProcessInstanceQuery.Builder().build();

    // And our search client returns stuff
    final var processInstanceEntityResponse = List.of(demoProcessInstance);

    when(searchClient.findAll(any(SearchQueryRequest.class), eq(ProcessInstanceEntity.class)))
        .thenReturn(processInstanceEntityResponse);
    when(authorizationQueryStrategy.applyAuthorizationToQuery(
            any(SearchQueryRequest.class), any(SecurityContext.class), any()))
        .thenAnswer(i -> i.getArgument(0));

    // When we search
    final List<ProcessInstanceEntity> searchResult =
        queryExecutor.findAll(searchAllQuery, ProcessInstanceEntity.class);

    assertThat(searchResult).hasSize(1);
    assertThat(searchResult.getFirst().processInstanceKey())
        .isEqualTo(demoProcessInstance.processInstanceKey());
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
