/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients;

import static io.camunda.webapps.schema.entities.listview.ProcessInstanceState.ACTIVE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.auth.AuthorizationQueryStrategy;
import io.camunda.search.clients.core.SearchQueryHit;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.core.SearchQueryResponse;
import io.camunda.search.clients.query.SearchQuery;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.clients.transformers.filter.ProcessDefinitionFilterTransformer;
import io.camunda.search.clients.transformers.filter.UserFilterTransformer;
import io.camunda.search.clients.types.TypedValue;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.filter.ProcessDefinitionFilter;
import io.camunda.search.filter.UserFilter;
import io.camunda.search.query.ProcessDefinitionQuery;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.UserQuery;
import io.camunda.security.auth.Authentication;
import io.camunda.security.auth.SecurityContext;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.webapps.schema.descriptors.index.ProcessIndex;
import io.camunda.webapps.schema.descriptors.index.UserIndex;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SearchClientBasedQueryExecutorTest {

  private final ProcessInstanceForListViewEntity demoProcessInstance =
      new ProcessInstanceForListViewEntity()
          .setProcessInstanceKey(123L)
          .setProcessName("Demo Process")
          .setBpmnProcessId("demoProcess")
          .setProcessVersion(5)
          .setProcessVersionTag("42")
          .setProcessDefinitionKey(789L)
          .setTenantId("default")
          .setStartDate(OffsetDateTime.parse("2024-01-01T00:00:00Z"))
          .setState(ACTIVE)
          .setIncident(false);

  @Mock private DocumentBasedSearchClient searchClient;
  @Mock private AuthorizationQueryStrategy authorizationQueryStrategy;
  private final ServiceTransformers serviceTransformers =
      ServiceTransformers.newInstance(new IndexDescriptors("", true));

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
    final SearchQueryResponse<ProcessInstanceForListViewEntity> processInstanceEntityResponse =
        createProcessInstanceEntityResponse(demoProcessInstance);

    when(searchClient.search(
            any(SearchQueryRequest.class), eq(ProcessInstanceForListViewEntity.class)))
        .thenReturn(processInstanceEntityResponse);
    when(authorizationQueryStrategy.applyAuthorizationToQuery(
            any(SearchQueryRequest.class), any(SecurityContext.class), any()))
        .thenAnswer(i -> i.getArgument(0));

    // When we search
    final SearchQueryResult<ProcessInstanceEntity> searchResult =
        queryExecutor.search(searchAllQuery, ProcessInstanceForListViewEntity.class);

    assertThat(searchResult.total()).isEqualTo(1);
    final List<ProcessInstanceEntity> items = searchResult.items();
    assertThat(items).hasSize(1);
    assertThat(items.getFirst().processInstanceKey())
        .isEqualTo(demoProcessInstance.getProcessInstanceKey());
  }

  @Test
  void shouldFindAllUsingTransformers() {
    // Given our search Query
    final var searchAllQuery = new ProcessInstanceQuery.Builder().build();

    // And our search client returns stuff
    final var processInstanceEntityResponse = List.of(demoProcessInstance);

    when(searchClient.findAll(
            any(SearchQueryRequest.class), eq(ProcessInstanceForListViewEntity.class)))
        .thenReturn(processInstanceEntityResponse);
    when(authorizationQueryStrategy.applyAuthorizationToQuery(
            any(SearchQueryRequest.class), any(SecurityContext.class), any()))
        .thenAnswer(i -> i.getArgument(0));

    // When we search
    final List<ProcessInstanceEntity> searchResult =
        queryExecutor.findAll(searchAllQuery, ProcessInstanceForListViewEntity.class);

    assertThat(searchResult).hasSize(1);
    assertThat(searchResult.getFirst().processInstanceKey())
        .isEqualTo(demoProcessInstance.getProcessInstanceKey());
  }

  @Test
  void shouldIncludeTenantFilterForTenantScopedEntities() {
    // given
    final var tenantIds = List.of("<default>", "T1");
    final SearchClientBasedQueryExecutor queryExecutor =
        new SearchClientBasedQueryExecutor(
            searchClient,
            serviceTransformers,
            AuthorizationQueryStrategy.NONE,
            SecurityContext.of(
                builder ->
                    builder.withAuthentication(
                        Authentication.of(a -> a.user("foo").tenants(tenantIds)))));

    final var query =
        new ProcessDefinitionQuery.Builder()
            .filter(new ProcessDefinitionFilter.Builder().processDefinitionIds("x").build())
            .build();

    // when
    final SearchQueryRequest searchQueryRequest =
        queryExecutor.executeSearch(query, Function.identity());

    // then
    assertThat(searchQueryRequest.query())
        .isEqualTo(
            SearchQuery.of(
                q ->
                    q.bool(
                        b ->
                            b.must(
                                List.of(
                                    new ProcessDefinitionFilterTransformer(
                                            new ProcessIndex("", true))
                                        .toSearchQuery(query.filter()),
                                    SearchQuery.of(
                                        q2 ->
                                            q2.terms(
                                                t ->
                                                    t.field(ProcessIndex.TENANT_ID)
                                                        .terms(
                                                            TypedValue.of(
                                                                tenantIds, TypedValue::of)))))))));
  }

  @Test
  void shouldNotIncludeTenantFilterForNonTenantScopedEntities() {
    // given
    final var tenantIds = List.of("<default>", "T1");
    final SearchClientBasedQueryExecutor queryExecutor =
        new SearchClientBasedQueryExecutor(
            searchClient,
            serviceTransformers,
            AuthorizationQueryStrategy.NONE,
            SecurityContext.of(
                builder ->
                    builder.withAuthentication(
                        Authentication.of(a -> a.user("foo").tenants(tenantIds)))));

    final var query =
        new UserQuery.Builder().filter(new UserFilter.Builder().name("x").build()).build();

    // when
    final SearchQueryRequest searchQueryRequest =
        queryExecutor.executeSearch(query, Function.identity());

    // then
    assertThat(searchQueryRequest.query())
        .isEqualTo(
            new UserFilterTransformer(new UserIndex("", true)).toSearchQuery(query.filter()));
  }

  private SearchQueryResponse<ProcessInstanceForListViewEntity> createProcessInstanceEntityResponse(
      final ProcessInstanceForListViewEntity demoProcessInstance) {
    final SearchQueryHit<ProcessInstanceForListViewEntity> hit =
        new SearchQueryHit.Builder<ProcessInstanceForListViewEntity>()
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
