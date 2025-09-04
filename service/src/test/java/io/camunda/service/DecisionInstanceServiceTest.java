/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.DecisionInstanceSearchClient;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.exception.ResourceAccessDeniedException;
import io.camunda.search.query.DecisionInstanceQuery;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.service.authorization.Authorizations;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DecisionInstanceServiceTest {

  private DecisionInstanceServices services;
  private DecisionInstanceSearchClient client;

  @BeforeEach
  public void before() {
    client = mock(DecisionInstanceSearchClient.class);
    when(client.withSecurityContext(any())).thenReturn(client);
    services =
        new DecisionInstanceServices(
            mock(BrokerClient.class),
            mock(SecurityContextProvider.class),
            client,
            null,
            mock(ApiServicesExecutorProvider.class));
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
  void shouldGetDecisionInstanceById() {
    // given
    final var decisionInstanceId = "1-1";
    final var decisionDefinitionKey = "dd1";
    final var decisionInstanceEntity = mock(DecisionInstanceEntity.class);
    when(decisionInstanceEntity.decisionDefinitionId()).thenReturn(decisionDefinitionKey);
    when(client.getDecisionInstance(any(String.class))).thenReturn(decisionInstanceEntity);

    // when
    services.getById(decisionInstanceId);

    // then
    verify(client).getDecisionInstance("1-1");
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
  void getByIdShouldReturnForbiddenForUnauthorizedDecisionDefinition() {
    // given
    final var decisionInstanceId = "1-1";
    final var decisionDefinitionKey = "dd1";
    final var decisionInstanceEntity = mock(DecisionInstanceEntity.class);
    when(decisionInstanceEntity.decisionDefinitionId()).thenReturn(decisionDefinitionKey);
    when(client.getDecisionInstance(eq("1-1")))
        .thenThrow(
            new ResourceAccessDeniedException(Authorizations.DECISION_INSTANCE_READ_AUTHORIZATION));

    // when
    final ThrowingCallable executable = () -> services.getById(decisionInstanceId);

    // then
    final var exception =
        assertThatExceptionOfType(ServiceException.class).isThrownBy(executable).actual();
    assertThat(exception.getMessage())
        .isEqualTo(
            "Unauthorized to perform operation 'READ_DECISION_INSTANCE' on resource 'DECISION_DEFINITION'");
    assertThat(exception.getStatus()).isEqualTo(Status.FORBIDDEN);
  }
}
