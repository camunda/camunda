/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.DecisionInstanceSearchClient;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.query.DecisionInstanceQuery;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.Authentication;
import io.camunda.security.auth.Authorization;
import io.camunda.service.exception.ForbiddenException;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

class DecisionInstanceServiceTest {

  private DecisionInstanceServices services;
  private DecisionInstanceSearchClient client;
  private SecurityContextProvider securityContextProvider;
  private Authentication authentication;

  @BeforeEach
  public void before() {
    client = mock(DecisionInstanceSearchClient.class);
    when(client.withSecurityContext(any())).thenReturn(client);
    securityContextProvider = mock(SecurityContextProvider.class);
    authentication = mock(Authentication.class);
    services =
        new DecisionInstanceServices(
            mock(BrokerClient.class), securityContextProvider, client, authentication);
  }

  @Test
  void shouldReturnDecisionInstances() {
    // given
    final var result = mock(SearchQueryResult.class);
    when(client.searchDecisionInstances(any())).thenReturn(result);

    final DecisionInstanceQuery searchQuery =
        SearchQueryBuilders.decisionInstanceSearchQuery().build();

    // when
    final SearchQueryResult<DecisionInstanceEntity> searchQueryResult =
        services.search(searchQuery);

    // then
    assertThat(searchQueryResult).isEqualTo(result);
  }

  @Test
  void shouldGetDecisionInstanceByKey() {
    // given
    final Long decisionInstanceKey = 1L;
    final var decisionDefinitionKey = "dd1";
    final var result = mock(SearchQueryResult.class);
    when(result.total()).thenReturn(1L);
    final var decisionInstanceEntity = mock(DecisionInstanceEntity.class);
    when(decisionInstanceEntity.decisionId()).thenReturn(decisionDefinitionKey);
    when(result.items()).thenReturn(List.of(decisionInstanceEntity));
    when(client.searchDecisionInstances(any())).thenReturn(result);
    when(securityContextProvider.isAuthorized(
            decisionDefinitionKey,
            authentication,
            Authorization.of(a -> a.decisionDefinition().readInstance())))
        .thenReturn(true);

    // when
    services.getByKey(decisionInstanceKey);

    // then
    verify(client)
        .searchDecisionInstances(
            SearchQueryBuilders.decisionInstanceSearchQuery(
                q -> q.filter(f -> f.decisionInstanceKeys(decisionInstanceKey))));
  }

  @Test
  void shouldSearchDecisionInstances() {
    // given
    final DecisionInstanceQuery query =
        SearchQueryBuilders.decisionInstanceSearchQuery(
            q ->
                q.filter(f -> f.tenantIds("tenant1"))
                    .sort(s -> s.evaluationDate().asc())
                    .page(p -> p.size(20)));

    // when
    services.search(query);

    // then
    verify(client)
        .searchDecisionInstances(
            SearchQueryBuilders.decisionInstanceSearchQuery(
                q ->
                    q.filter(f -> f.tenantIds("tenant1"))
                        .sort(s -> s.evaluationDate().asc())
                        .page(p -> p.size(20))
                        .resultConfig(
                            r -> r.includeEvaluatedInputs(false).includeEvaluatedOutputs(false))));
  }

  @Test
  void getByKeyShouldReturnForbiddenForUnauthorizedDecisionDefinition() {
    // given
    final Long decisionInstanceKey = 1L;
    final var decisionDefinitionKey = "dd1";
    final var result = mock(SearchQueryResult.class);
    when(result.total()).thenReturn(1L);
    final var decisionInstanceEntity = mock(DecisionInstanceEntity.class);
    when(decisionInstanceEntity.decisionId()).thenReturn(decisionDefinitionKey);
    when(result.items()).thenReturn(List.of(decisionInstanceEntity));
    when(client.searchDecisionInstances(any())).thenReturn(result);
    when(securityContextProvider.isAuthorized(
            decisionDefinitionKey,
            authentication,
            Authorization.of(a -> a.decisionDefinition().readInstance())))
        .thenReturn(false);

    // when
    final Executable executable = () -> services.getByKey(decisionInstanceKey);

    // then
    final var exception = assertThrows(ForbiddenException.class, executable);
    assertThat(exception.getMessage())
        .isEqualTo(
            "Unauthorized to perform operation 'READ_INSTANCE' on resource 'DECISION_DEFINITION'");
  }
}
