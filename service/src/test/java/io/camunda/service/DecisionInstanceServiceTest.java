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
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.DecisionInstanceSearchClient;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.exception.ResourceAccessDeniedException;
import io.camunda.search.query.DecisionInstanceQuery;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.authorization.Authorizations;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DecisionInstanceServiceTest {

  private static final String DECISION_INSTANCE_ID = "1-1";
  private static final String DECISION_DEFINITION_KEY = "dd1";
  private static final String NON_EXISTENT_ID = "non-existent";

  private DecisionInstanceServices services;
  private DecisionInstanceSearchClient client;
  private BrokerClient brokerClient;

  @BeforeEach
  void before() {
    client = mock(DecisionInstanceSearchClient.class);
    when(client.withSecurityContext(any())).thenReturn(client);

    brokerClient = mock(BrokerClient.class);
    mockSuccessfulBrokerResponse();

    final var brokerRequestAuthorizationConverter = mock(BrokerRequestAuthorizationConverter.class);
    when(brokerRequestAuthorizationConverter.convert(any())).thenReturn(null);

    final var executorProvider = mock(ApiServicesExecutorProvider.class);
    when(executorProvider.getExecutor()).thenReturn(ForkJoinPool.commonPool());

    services =
        new DecisionInstanceServices(
            brokerClient,
            mock(SecurityContextProvider.class),
            client,
            mock(CamundaAuthentication.class),
            executorProvider,
            brokerRequestAuthorizationConverter);
  }

  // --------------------------------------------------------------------------
  // Search
  // --------------------------------------------------------------------------

  @Test
  void shouldReturnDecisionInstances() {
    // given
    final var result = mockSearchResult();

    final DecisionInstanceQuery query = SearchQueryBuilders.decisionInstanceSearchQuery().build();

    // when
    final var response = services.search(query);

    // then
    assertThat(response).isEqualTo(result);
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

  // --------------------------------------------------------------------------
  // Get by ID
  // --------------------------------------------------------------------------

  @Test
  void shouldGetDecisionInstanceById() {
    // given
    mockExistingDecisionInstance();

    // when
    services.getById(DECISION_INSTANCE_ID);

    // then
    verify(client).getDecisionInstance(DECISION_INSTANCE_ID);
  }

  @Test
  void getByIdShouldReturnForbiddenForUnauthorizedDecisionDefinition() {
    // given
    mockUnauthorizedDecisionInstanceRead();

    // when
    final ThrowingCallable executable = () -> services.getById(DECISION_INSTANCE_ID);

    // then
    final var exception =
        assertThatExceptionOfType(ServiceException.class).isThrownBy(executable).actual();

    assertThat(exception.getMessage())
        .isEqualTo(
            "Unauthorized to perform operation 'READ_DECISION_INSTANCE' on resource 'DECISION_DEFINITION'");
    assertThat(exception.getStatus()).isEqualTo(Status.FORBIDDEN);
  }

  // --------------------------------------------------------------------------
  // Delete
  // --------------------------------------------------------------------------

  @Test
  void shouldDeleteDecisionInstance() {
    // given
    mockExistingDecisionInstance();

    // when
    final var result = services.deleteDecisionInstance(DECISION_INSTANCE_ID, null);

    // then
    assertThat(result).isNotNull();
    verify(brokerClient).sendRequest(any());
  }

  @Test
  void shouldThrowExceptionWhenDecisionInstanceNotFoundDuringDeletion() {
    // given
    mockDecisionInstanceNotFound();

    // when
    final ThrowingCallable executable =
        () -> services.deleteDecisionInstance(NON_EXISTENT_ID, null);

    // then
    final var exception =
        assertThatExceptionOfType(ServiceException.class).isThrownBy(executable).actual();

    assertThat(exception.getStatus()).isEqualTo(Status.NOT_FOUND);
  }

  @Test
  void shouldThrowForbiddenExceptionWhenUnauthorizedToDeleteDecisionInstance() {
    // given
    mockExistingDecisionInstance();
    mockBrokerForbidden();

    // when
    final ThrowingCallable executable =
        () -> services.deleteDecisionInstance(DECISION_INSTANCE_ID, null).join();

    // then
    final var completionException =
        assertThatExceptionOfType(java.util.concurrent.CompletionException.class)
            .isThrownBy(executable)
            .actual();

    assertThat(completionException.getCause()).isInstanceOf(ServiceException.class);
    final var exception = (ServiceException) completionException.getCause();

    assertThat(exception.getMessage())
        .contains(
            "Unauthorized to perform operation 'DELETE_DECISION_INSTANCE' on resource 'DECISION_DEFINITION'");
    assertThat(exception.getStatus()).isEqualTo(Status.INTERNAL);
  }

  // --------------------------------------------------------------------------
  // Helper methods
  // --------------------------------------------------------------------------

  private SearchQueryResult<DecisionInstanceEntity> mockSearchResult() {
    final var result = mock(SearchQueryResult.class);
    when(client.searchDecisionInstances(any())).thenReturn(result);
    return result;
  }

  private void mockExistingDecisionInstance() {
    final var entity = mock(DecisionInstanceEntity.class);
    when(entity.decisionDefinitionId()).thenReturn(DECISION_DEFINITION_KEY);
    when(client.getDecisionInstance(DECISION_INSTANCE_ID)).thenReturn(entity);
  }

  private void mockUnauthorizedDecisionInstanceRead() {
    when(client.getDecisionInstance(DECISION_INSTANCE_ID))
        .thenThrow(
            new ResourceAccessDeniedException(Authorizations.DECISION_INSTANCE_READ_AUTHORIZATION));
  }

  private void mockDecisionInstanceNotFound() {
    when(client.getDecisionInstance(NON_EXISTENT_ID))
        .thenThrow(
            new CamundaSearchException(
                "Decision instance not found", CamundaSearchException.Reason.NOT_FOUND));
  }

  private void mockSuccessfulBrokerResponse() {
    final var record = mock(BatchOperationCreationRecord.class);
    final var response = new BrokerResponse<>(record);

    doReturn(CompletableFuture.completedFuture(response)).when(brokerClient).sendRequest(any());
  }

  private void mockBrokerForbidden() {
    doReturn(
            CompletableFuture.failedFuture(
                new ResourceAccessDeniedException(
                    Authorizations.DECISION_DEFINITION_DELETE_DECISION_INSTANCE_AUTHORIZATION)))
        .when(brokerClient)
        .sendRequest(any());
  }
}
