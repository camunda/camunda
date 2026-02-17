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
import static io.camunda.security.auth.Authorization.withAuthorization;
import static io.camunda.service.authorization.Authorizations.DECISION_INSTANCE_READ_AUTHORIZATION;

import io.camunda.search.clients.DecisionInstanceSearchClient;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.filter.DecisionInstanceFilter;
import io.camunda.search.query.DecisionInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.exception.ErrorMapper;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCreateBatchOperationRequest;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerDeleteHistoryRequest;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.history.HistoryDeletionRecord;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
import io.camunda.zeebe.protocol.record.value.HistoryDeletionType;
import java.util.concurrent.CompletableFuture;

public final class DecisionInstanceServices
    extends SearchQueryService<
        DecisionInstanceServices, DecisionInstanceQuery, DecisionInstanceEntity> {

  private final DecisionInstanceSearchClient decisionInstanceSearchClient;

  public DecisionInstanceServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityHandler,
      final DecisionInstanceSearchClient decisionInstanceSearchClient,
      final CamundaAuthentication authentication,
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    super(
        brokerClient,
        securityHandler,
        authentication,
        executorProvider,
        brokerRequestAuthorizationConverter);
    this.decisionInstanceSearchClient = decisionInstanceSearchClient;
  }

  @Override
  public DecisionInstanceServices withAuthentication(final CamundaAuthentication authentication) {
    return new DecisionInstanceServices(
        brokerClient,
        securityContextProvider,
        decisionInstanceSearchClient,
        authentication,
        executorProvider,
        brokerRequestAuthorizationConverter);
  }

  /**
   * Search for Decision Instances.
   *
   * <p>By default, evaluateInputs and evaluateOutputs are excluded from the returned Decision
   * Instances.
   */
  @Override
  public SearchQueryResult<DecisionInstanceEntity> search(final DecisionInstanceQuery query) {
    return executeSearchRequest(
        () ->
            decisionInstanceSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, DECISION_INSTANCE_READ_AUTHORIZATION))
                .searchDecisionInstances(
                    decisionInstanceSearchQuery(
                        q -> q.filter(query.filter()).sort(query.sort()).page(query.page()))));
  }

  /**
   * Get a Decision Instance by its ID.
   *
   * @param decisionInstanceId the ID of the decision instance
   * @return the Decision Instance
   * @throws CamundaSearchException unless the decision instance with the given ID exists exactly
   *     once
   */
  public DecisionInstanceEntity getById(final String decisionInstanceId) {
    return executeSearchRequest(
        () ->
            decisionInstanceSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication,
                        withAuthorization(
                            DECISION_INSTANCE_READ_AUTHORIZATION,
                            DecisionInstanceEntity::decisionDefinitionId)))
                .getDecisionInstance(decisionInstanceId));
  }

  /**
   * Deletes a decision instance and all associated decision evaluations.
   *
   * @param decisionInstanceKey the key of the decision instance to delete
   * @param operationReference optional operation reference for tracking
   * @return a CompletableFuture containing the batch operation creation record
   * @throws CamundaSearchException if no decision instance with the given key exists
   */
  public CompletableFuture<HistoryDeletionRecord> deleteDecisionInstance(
      final long decisionInstanceKey, final Long operationReference) {

    // make sure decision instance exists before deletion, otherwise return not found
    final var searchResult =
        search(
            decisionInstanceSearchQuery(
                q -> q.filter(f -> f.decisionInstanceKeys(decisionInstanceKey))));

    if (searchResult.items().isEmpty()) {
      throw ErrorMapper.mapSearchError(
          new CamundaSearchException(
              ERROR_ENTITY_BY_KEY_NOT_FOUND.formatted("Decision Instance", decisionInstanceKey),
              CamundaSearchException.Reason.NOT_FOUND));
    }

    final var decisionInstance = searchResult.items().getFirst();

    final var brokerRequest =
        new BrokerDeleteHistoryRequest()
            .setResourceKey(decisionInstanceKey)
            .setResourceType(HistoryDeletionType.DECISION_INSTANCE)
            .setDecisionDefinitionId(decisionInstance.decisionDefinitionId())
            .setTenantId(decisionInstance.tenantId());

    if (operationReference != null) {
      brokerRequest.setOperationReference(operationReference);
    }

    return sendBrokerRequest(brokerRequest);
  }

  public CompletableFuture<BatchOperationCreationRecord> deleteDecisionInstancesBatchOperation(
      final DecisionInstanceFilter filter) {
    final var brokerRequest =
        new BrokerCreateBatchOperationRequest()
            .setFilter(filter)
            .setBatchOperationType(BatchOperationType.DELETE_DECISION_INSTANCE)
            .setAuthentication(authentication);
    return sendBrokerRequest(brokerRequest);
  }
}
