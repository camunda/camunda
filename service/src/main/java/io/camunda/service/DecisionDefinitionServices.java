/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.search.query.SearchQueryBuilders.decisionDefinitionSearchQuery;
import static io.camunda.search.query.SearchQueryBuilders.decisionRequirementsSearchQuery;

import io.camunda.search.clients.DecisionDefinitionSearchClient;
import io.camunda.search.clients.DecisionRequirementSearchClient;
import io.camunda.search.entities.DecisionDefinitionEntity;
import io.camunda.search.query.DecisionDefinitionQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.Authentication;
import io.camunda.security.auth.Authorization;
import io.camunda.service.exception.ForbiddenException;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.util.ObjectBuilder;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import io.camunda.zeebe.broker.client.api.dto.BrokerResponse;
import io.camunda.zeebe.gateway.impl.broker.request.BrokerEvaluateDecisionRequest;
import io.camunda.zeebe.protocol.impl.record.value.decision.DecisionEvaluationRecord;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public final class DecisionDefinitionServices
    extends SearchQueryService<
        DecisionDefinitionServices, DecisionDefinitionQuery, DecisionDefinitionEntity> {

  private final DecisionDefinitionSearchClient decisionDefinitionSearchClient;
  private final DecisionRequirementSearchClient decisionRequirementSearchClient;

  public DecisionDefinitionServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final DecisionDefinitionSearchClient decisionDefinitionSearchClient,
      final DecisionRequirementSearchClient decisionRequirementSearchClient,
      final Authentication authentication) {
    super(brokerClient, securityContextProvider, authentication);
    this.decisionDefinitionSearchClient = decisionDefinitionSearchClient;
    this.decisionRequirementSearchClient = decisionRequirementSearchClient;
  }

  @Override
  public DecisionDefinitionServices withAuthentication(final Authentication authentication) {
    return new DecisionDefinitionServices(
        brokerClient,
        securityContextProvider,
        decisionDefinitionSearchClient,
        decisionRequirementSearchClient,
        authentication);
  }

  @Override
  public SearchQueryResult<DecisionDefinitionEntity> search(final DecisionDefinitionQuery query) {
    return decisionDefinitionSearchClient
        .withSecurityContext(
            securityContextProvider.provideSecurityContext(
                authentication,
                Authorization.of(a -> a.decisionDefinition().readDecisionDefinition())))
        .searchDecisionDefinitions(query);
  }

  public SearchQueryResult<DecisionDefinitionEntity> search(
      final Function<DecisionDefinitionQuery.Builder, ObjectBuilder<DecisionDefinitionQuery>> fn) {
    return search(decisionDefinitionSearchQuery(fn));
  }

  public String getDecisionDefinitionXml(final long decisionKey) {
    final var decisionDefinition = getByKey(decisionKey);

    final Long decisionRequirementsKey = decisionDefinition.decisionRequirementsKey();
    final var decisionRequirementsQuery =
        decisionRequirementsSearchQuery(
            q ->
                q.filter(f -> f.decisionRequirementsKeys(decisionRequirementsKey))
                    .resultConfig(r -> r.includeXml(true)));
    final var decisionRequirements =
        getSingleResultOrThrow(
            decisionRequirementSearchClient
                .withSecurityContext(securityContextProvider.provideSecurityContext(authentication))
                .searchDecisionRequirements(decisionRequirementsQuery),
            decisionRequirementsKey,
            "Decision requirements");
    return decisionRequirements.xml();
  }

  public DecisionDefinitionEntity getByKey(final long decisionKey) {
    final var result =
        decisionDefinitionSearchClient
            .withSecurityContext(securityContextProvider.provideSecurityContext(authentication))
            .searchDecisionDefinitions(
                decisionDefinitionSearchQuery(
                    q -> q.filter(f -> f.decisionDefinitionKeys(decisionKey))));
    final var decisionDefinitionEntity =
        getSingleResultOrThrow(result, decisionKey, "Decision definition");
    final var authorization =
        Authorization.of(a -> a.decisionDefinition().readDecisionDefinition());
    if (!securityContextProvider.isAuthorized(
        decisionDefinitionEntity.decisionDefinitionId(), authentication, authorization)) {
      throw new ForbiddenException(authorization);
    }
    return decisionDefinitionEntity;
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
