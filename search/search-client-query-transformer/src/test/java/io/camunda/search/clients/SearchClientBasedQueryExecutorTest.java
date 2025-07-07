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

import io.camunda.search.clients.core.SearchQueryHit;
import io.camunda.search.clients.core.SearchQueryRequest;
import io.camunda.search.clients.core.SearchQueryResponse;
import io.camunda.search.clients.transformers.ServiceTransformers;
import io.camunda.search.entities.ProcessInstanceEntity;
import io.camunda.search.query.ProcessInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.SecurityContext;
import io.camunda.security.resource.AuthorizationResult;
import io.camunda.security.resource.ResourceAccessPolicy;
import io.camunda.security.resource.ResourceAccessResult;
import io.camunda.security.resource.TenantResult;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.webapps.schema.entities.listview.ProcessInstanceForListViewEntity;
import java.time.OffsetDateTime;
import java.util.List;
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
  @Mock private ResourceAccessPolicy resourceAccessPolicy;
  private final ServiceTransformers serviceTransformers =
      ServiceTransformers.newInstance(new IndexDescriptors("", true));

  private SearchClientBasedQueryExecutor queryExecutor;

  @BeforeEach
  void setUp() {
    queryExecutor =
        new SearchClientBasedQueryExecutor(
            searchClient,
            serviceTransformers,
            resourceAccessPolicy,
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
    when(resourceAccessPolicy.applySecurityContext(any(SecurityContext.class)))
        .thenReturn(
            ResourceAccessResult.of(
                b ->
                    b.authorizationResult(AuthorizationResult.successful())
                        .tenantResult(TenantResult.successful())));

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
    when(resourceAccessPolicy.applySecurityContext(any(SecurityContext.class)))
        .thenReturn(
            ResourceAccessResult.of(
                b ->
                    b.authorizationResult(AuthorizationResult.successful())
                        .tenantResult(TenantResult.successful())));

    // When we search
    final List<ProcessInstanceEntity> searchResult =
        queryExecutor.findAll(searchAllQuery, ProcessInstanceForListViewEntity.class);

    assertThat(searchResult).hasSize(1);
    assertThat(searchResult.getFirst().processInstanceKey())
        .isEqualTo(demoProcessInstance.getProcessInstanceKey());
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
