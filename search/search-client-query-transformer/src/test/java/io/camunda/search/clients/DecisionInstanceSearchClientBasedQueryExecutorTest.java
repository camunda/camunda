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
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionDefinitionType;
import io.camunda.search.entities.DecisionInstanceEntity.DecisionInstanceState;
import io.camunda.search.query.DecisionInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.SecurityContext;
import io.camunda.webapps.schema.descriptors.IndexDescriptors;
import io.camunda.webapps.schema.entities.dmn.DecisionType;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DecisionInstanceSearchClientBasedQueryExecutorTest {

  private final DecisionInstanceEntity domainEntity =
      new DecisionInstanceEntity(
          "123-1",
          123L,
          DecisionInstanceState.EVALUATED,
          OffsetDateTime.parse("2024-06-05T08:29:15.027+00:00"),
          "failure",
          2251799813688736L,
          6755399441058457L,
          "tenantId",
          "ddi",
          123456L,
          "ddn",
          0,
          DecisionDefinitionType.DECISION_TABLE,
          "result",
          null,
          null);

  private final io.camunda.webapps.schema.entities.dmn.DecisionInstanceEntity documentEntity =
      new io.camunda.webapps.schema.entities.dmn.DecisionInstanceEntity()
          .setKey(123L)
          .setId("123-1")
          .setState(io.camunda.webapps.schema.entities.dmn.DecisionInstanceState.EVALUATED)
          .setEvaluationDate(OffsetDateTime.parse("2024-06-05T08:29:15.027+00:00"))
          .setEvaluationFailure("failure")
          .setProcessDefinitionKey(2251799813688736L)
          .setProcessInstanceKey(6755399441058457L)
          .setTenantId("tenantId")
          .setDecisionId("ddi")
          .setDecisionDefinitionId("123456")
          .setDecisionName("ddn")
          .setDecisionVersion(0)
          .setDecisionType(DecisionType.DECISION_TABLE)
          .setResult("result")
          .setEvaluatedOutputs(null)
          .setEvaluatedInputs(null);

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
    final var searchAllQuery = new DecisionInstanceQuery.Builder().build();

    // And our search client returns stuff
    final var decisionInstanceEntityResponse = createDecisionInstanceEntityResponse(documentEntity);

    when(searchClient.search(
            any(SearchQueryRequest.class),
            eq(io.camunda.webapps.schema.entities.dmn.DecisionInstanceEntity.class)))
        .thenReturn(decisionInstanceEntityResponse);
    when(authorizationQueryStrategy.applyAuthorizationToQuery(
            any(SearchQueryRequest.class), any(SecurityContext.class), any()))
        .thenAnswer(i -> i.getArgument(0));

    // When we search
    final SearchQueryResult<DecisionInstanceEntity> searchResult =
        queryExecutor.search(
            searchAllQuery, io.camunda.webapps.schema.entities.dmn.DecisionInstanceEntity.class);

    assertThat(searchResult.total()).isEqualTo(1);
    final List<DecisionInstanceEntity> items = searchResult.items();
    assertThat(items).hasSize(1);
    assertThat(items.getFirst()).isEqualTo(domainEntity);
  }

  @Test
  void shouldFindAllUsingTransformers() {
    // Given our search Query
    final var searchAllQuery = new DecisionInstanceQuery.Builder().build();

    // And our search client returns stuff
    final var decisionInstanceEntityResponse = List.of(documentEntity);

    when(searchClient.findAll(
            any(SearchQueryRequest.class),
            eq(io.camunda.webapps.schema.entities.dmn.DecisionInstanceEntity.class)))
        .thenReturn(decisionInstanceEntityResponse);
    when(authorizationQueryStrategy.applyAuthorizationToQuery(
            any(SearchQueryRequest.class), any(SecurityContext.class), any()))
        .thenAnswer(i -> i.getArgument(0));

    // When we search
    final List<DecisionInstanceEntity> searchResult =
        queryExecutor.findAll(
            searchAllQuery, io.camunda.webapps.schema.entities.dmn.DecisionInstanceEntity.class);

    assertThat(searchResult).hasSize(1);
    assertThat(searchResult.getFirst()).isEqualTo(domainEntity);
  }

  private SearchQueryResponse<io.camunda.webapps.schema.entities.dmn.DecisionInstanceEntity>
      createDecisionInstanceEntityResponse(
          final io.camunda.webapps.schema.entities.dmn.DecisionInstanceEntity documentEntity) {
    final var hit =
        new SearchQueryHit.Builder<io.camunda.webapps.schema.entities.dmn.DecisionInstanceEntity>()
            .id("1000")
            .source(documentEntity)
            .build();

    return SearchQueryResponse.of(
        (f) -> {
          f.totalHits(1).hits(List.of(hit));
          return f;
        });
  }
}
