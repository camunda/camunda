/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.search.exception.ErrorMessages.ERROR_ENTITY_BY_KEY_NOT_FOUND;
import static io.camunda.search.query.SearchQueryBuilders.decisionInstanceSearchQuery;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.instancio.Select.field;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.search.clients.DecisionInstanceSearchClient;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.exception.CamundaSearchException.Reason;
import io.camunda.search.exception.ResourceAccessDeniedException;
import io.camunda.search.filter.FilterBuilders;
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
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCreateBatchOperationRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerDeleteHistoryRequest;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.history.HistoryDeletionRecord;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionType;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.instancio.Instancio;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DecisionInstanceServiceTest {

  private static final Long DECISION_INSTANCE_KEY = 1L;
  private static final Long NON_EXISTENT_KEY = 999L;
  private static final String DECISION_INSTANCE_ID = "1-1";
  private static final String DECISION_DEFINITION_ID = "dd1";

  private DecisionInstanceServices services;
  private DecisionInstanceSearchClient client;
  private SecurityContextProvider securityContextProvider;
  private CamundaAuthentication authentication;
  private BrokerClient brokerClient;
  private ApiServicesExecutorProvider executorProvider;
  private BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter;

  @BeforeEach
  void before() {
    client = mock(DecisionInstanceSearchClient.class);
    authentication = CamundaAuthentication.none();
    when(client.withSecurityContext(any())).thenReturn(client);
    securityContextProvider = mock(SecurityContextProvider.class);
    brokerClient = mock(BrokerClient.class);
    executorProvider = mock(ApiServicesExecutorProvider.class);
    when(executorProvider.getExecutor()).thenReturn(ForkJoinPool.commonPool());
    brokerRequestAuthorizationConverter = mock(BrokerRequestAuthorizationConverter.class);
    services =
        new DecisionInstanceServices(
            brokerClient,
            securityContextProvider,
            client,
            authentication,
            executorProvider,
            brokerRequestAuthorizationConverter);
  }

  // --------------------------------------------------------------------------
  // Search
  // --------------------------------------------------------------------------

  @Test
  void shouldReturnDecisionInstances() {
    // given
    final var result = mock(SearchQueryResult.class);
    when(client.searchDecisionInstances(any())).thenReturn(result);
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

  @Test
  void shouldSearchByDecisionInstanceKeyShouldReturnForbiddenForUnauthorizedDecisionDefinition() {
    // given
    when(client.searchDecisionInstances(any(DecisionInstanceQuery.class)))
        .thenThrow(
            new ResourceAccessDeniedException(Authorizations.DECISION_INSTANCE_READ_AUTHORIZATION));

    // when
    final ThrowingCallable executable =
        () ->
            services.search(
                decisionInstanceSearchQuery(
                    q -> q.filter(f -> f.decisionInstanceKeys(DECISION_INSTANCE_KEY))));

    // then
    final var exception =
        (ServiceException)
            assertThatThrownBy(executable).isInstanceOf(ServiceException.class).actual();
    assertThat(exception.getMessage())
        .isEqualTo(
            "Unauthorized to perform operation 'READ_DECISION_INSTANCE' on resource 'DECISION_DEFINITION'");
    assertThat(exception.getStatus()).isEqualTo(Status.FORBIDDEN);
  }

  // --------------------------------------------------------------------------
  // Get by ID
  // --------------------------------------------------------------------------

  @Test
  void shouldGetDecisionInstanceById() {
    // given
    final var entity =
        Instancio.of(DecisionInstanceEntity.class)
            .set(field(DecisionInstanceEntity::decisionInstanceId), DECISION_INSTANCE_ID)
            .create();
    when(client.getDecisionInstance(DECISION_INSTANCE_ID)).thenReturn(entity);

    // when
    final var result = services.getById(DECISION_INSTANCE_ID);

    // then
    verify(client).getDecisionInstance(DECISION_INSTANCE_ID);
    assertThat(result.decisionInstanceId()).isEqualTo(DECISION_INSTANCE_ID);
  }

  // --------------------------------------------------------------------------
  // Delete
  // --------------------------------------------------------------------------

  @Test
  void shouldDeleteDecisionInstance() {
    // given
    final var decisionInstanceKey = 123L;
    final var tenantId = "tenantId";

    final var entity = mock(DecisionInstanceEntity.class);
    when(entity.decisionDefinitionId()).thenReturn(DECISION_DEFINITION_ID);
    when(entity.tenantId()).thenReturn(tenantId);

    final var result = mock(SearchQueryResult.class);
    when(result.items()).thenReturn(List.of(entity));
    when(client.searchDecisionInstances(any(DecisionInstanceQuery.class))).thenReturn(result);

    final var record =
        new HistoryDeletionRecord()
            .setResourceKey(decisionInstanceKey)
            .setResourceType(HistoryDeletionType.DECISION_INSTANCE)
            .setDecisionDefinitionId(DECISION_DEFINITION_ID)
            .setTenantId(tenantId);
    final var captor = ArgumentCaptor.forClass(BrokerDeleteHistoryRequest.class);
    when(brokerClient.sendRequest(captor.capture()))
        .thenReturn(CompletableFuture.completedFuture(new BrokerResponse<>(record)));

    // when
    services.deleteDecisionInstance(decisionInstanceKey, null).join();

    // then
    final var brokerRequest = (HistoryDeletionRecord) captor.getValue().getRequestWriter();
    assertThat(brokerRequest.getResourceKey()).isEqualTo(decisionInstanceKey);
    assertThat(brokerRequest.getResourceType()).isEqualTo(HistoryDeletionType.DECISION_INSTANCE);
    assertThat(brokerRequest.getDecisionDefinitionId()).isEqualTo(DECISION_DEFINITION_ID);
    assertThat(brokerRequest.getTenantId()).isEqualTo(tenantId);
  }

  @Test
  void shouldNotDeleteDecisionInstance() {
    // given
    when(client.searchDecisionInstances(any(DecisionInstanceQuery.class)))
        .thenThrow(
            new CamundaSearchException(
                ERROR_ENTITY_BY_KEY_NOT_FOUND.formatted("Decision Instance", NON_EXISTENT_KEY),
                Reason.NOT_FOUND));

    // when/then
    assertThatThrownBy(() -> services.deleteDecisionInstance(NON_EXISTENT_KEY, null).join())
        .isInstanceOf(ServiceException.class)
        .hasMessage("Decision Instance with key '" + NON_EXISTENT_KEY + "' not found");
  }

  @Test
  void shouldDeleteDecisionInstanceBatchOperationWithResult() {
    // given
    final var filter =
        FilterBuilders.decisionInstance(
            b -> b.decisionDefinitionIds("test-decision-definition-id"));

    final long batchOperationKey = 123L;
    final var record = new BatchOperationCreationRecord();
    record.setBatchOperationKey(batchOperationKey);
    record.setBatchOperationType(BatchOperationType.DELETE_DECISION_INSTANCE);

    final var captor = ArgumentCaptor.forClass(BrokerCreateBatchOperationRequest.class);
    when(brokerClient.sendRequest(captor.capture()))
        .thenReturn(CompletableFuture.completedFuture(new BrokerResponse<>(record)));

    // when
    final var result = services.deleteDecisionInstancesBatchOperation(filter).join();

    // then
    assertThat(result.getBatchOperationKey()).isEqualTo(batchOperationKey);
    assertThat(result.getBatchOperationType())
        .isEqualTo(BatchOperationType.DELETE_DECISION_INSTANCE);

    // and our request got enriched
    final var enrichedRecord = captor.getValue().getRequestWriter();

    assertThat(
            MsgPackConverter.convertToObject(
                enrichedRecord.getAuthenticationBuffer(), CamundaAuthentication.class))
        .isEqualTo(authentication);
    assertThat(enrichedRecord.getEntityFilter()).contains("test-decision-definition-id");
  }
}
