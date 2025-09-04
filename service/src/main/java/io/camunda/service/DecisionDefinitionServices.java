/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.search.query.SearchQueryBuilders.decisionDefinitionSearchQuery;
import static io.camunda.security.auth.Authorization.withAuthorization;
import static io.camunda.service.authorization.Authorizations.DECISION_DEFINITION_READ_AUTHORIZATION;

import io.camunda.search.clients.DecisionDefinitionSearchClient;
import io.camunda.search.entities.DecisionDefinitionEntity;
import io.camunda.search.query.DecisionDefinitionQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.exception.ServiceException;
import io.camunda.service.exception.ServiceException.Status;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.util.ObjectBuilder;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerEvaluateDecisionRequest;
import io.camunda.zeebe.protocol.impl.record.value.decision.DecisionEvaluationRecord;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public final class DecisionDefinitionServices
    extends SearchQueryService<
        DecisionDefinitionServices, DecisionDefinitionQuery, DecisionDefinitionEntity> {

  private final DecisionDefinitionSearchClient decisionDefinitionSearchClient;
  private final DecisionRequirementsServices decisionRequirementServices;

  public DecisionDefinitionServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final DecisionDefinitionSearchClient decisionDefinitionSearchClient,
      final DecisionRequirementsServices decisionRequirementServices,
      final CamundaAuthentication authentication,
      final ApiServicesExecutorProvider executorProvider) {
    super(brokerClient, securityContextProvider, authentication, executorProvider);
    this.decisionDefinitionSearchClient = decisionDefinitionSearchClient;
    this.decisionRequirementServices = decisionRequirementServices;
  }

  @Override
  public DecisionDefinitionServices withAuthentication(final CamundaAuthentication authentication) {
    return new DecisionDefinitionServices(
        brokerClient,
        securityContextProvider,
        decisionDefinitionSearchClient,
        decisionRequirementServices,
        authentication,
        executorProvider);
  }

  @Override
  public SearchQueryResult<DecisionDefinitionEntity> search(final DecisionDefinitionQuery query) {
    return executeSearchRequest(
        () ->
            decisionDefinitionSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, DECISION_DEFINITION_READ_AUTHORIZATION))
                .searchDecisionDefinitions(query));
  }

  public SearchQueryResult<DecisionDefinitionEntity> search(
      final Function<DecisionDefinitionQuery.Builder, ObjectBuilder<DecisionDefinitionQuery>> fn) {
    return search(decisionDefinitionSearchQuery(fn));
  }

  public String getDecisionDefinitionXml(final long decisionKey) {
    return Optional.ofNullable(getByKey(decisionKey))
        .map(DecisionDefinitionEntity::decisionRequirementsKey)
        .map(
            k ->
                decisionRequirementServices
                    .withAuthentication(CamundaAuthentication.anonymous())
                    .getDecisionRequirementsXml(k))
        .orElseThrow(
            () ->
                new ServiceException(
                    "Decision Definition Xml by key %d not found".formatted(decisionKey),
                    Status.NOT_FOUND));
  }

  public DecisionDefinitionEntity getByKey(final long decisionKey) {
    return executeSearchRequest(
        () ->
            decisionDefinitionSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication,
                        withAuthorization(
                            DECISION_DEFINITION_READ_AUTHORIZATION,
                            DecisionDefinitionEntity::decisionDefinitionId)))
                .getDecisionDefinition(decisionKey));
  }

  public CompletableFuture<BrokerResponse<DecisionEvaluationRecord>> evaluateDecision(
      final String definitionId,
      final Long definitionKey,
      final Map<String, Object> variables,
      final String tenantId) {
    return sendBrokerRequestWithFullResponse(
        new BrokerEvaluateDecisionRequest()
            .setDecisionId(definitionId)
            .setDecisionKey(definitionKey)
            .setVariables(getDocumentOrEmpty(variables))
            .setTenantId(tenantId));
  }
}
