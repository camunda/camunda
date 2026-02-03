/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.search.query.SearchQueryBuilders.decisionInstanceSearchQuery;
import static io.camunda.security.auth.Authorization.withAuthorization;
import static io.camunda.service.authorization.Authorizations.DECISION_DEFINITION_DELETE_DECISION_INSTANCE_AUTHORIZATION;
import static io.camunda.service.authorization.Authorizations.DECISION_INSTANCE_READ_AUTHORIZATION;

import io.camunda.search.clients.DecisionInstanceSearchClient;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.filter.DecisionInstanceFilter;
import io.camunda.search.query.DecisionInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerCreateBatchOperationRequest;
import io.camunda.zeebe.protocol.impl.record.value.batchoperation.BatchOperationCreationRecord;
import io.camunda.zeebe.protocol.record.value.BatchOperationType;
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

  public CompletableFuture<BatchOperationCreationRecord> deleteDecisionInstance(
      final String decisionInstanceId, final Long operationReference) {

    // make sure decision instance exists before deletion, otherwise return not found
    final var decisionInstance = getById(decisionInstanceId);

    final var brokerRequest =
        new BrokerCreateBatchOperationRequest()
            .setFilter(
                new DecisionInstanceFilter.Builder()
                    .decisionInstanceIds(decisionInstanceId)
                    .build())
            .setBatchOperationType(BatchOperationType.DELETE_DECISION_INSTANCE)
            .setAuthentication(authentication)
            // the user only needs single instance delete permission, not batch
            .setAuthorizationCheck(
                Authorization.withAuthorization(
                    DECISION_DEFINITION_DELETE_DECISION_INSTANCE_AUTHORIZATION,
                    decisionInstance.decisionDefinitionId()));

    if (operationReference != null) {
      brokerRequest.setOperationReference(operationReference);
    }

    return sendBrokerRequest(brokerRequest);
  }
}
