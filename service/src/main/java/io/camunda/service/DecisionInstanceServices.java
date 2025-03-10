/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.search.query.SearchQueryBuilders.decisionInstanceSearchQuery;
import static java.util.Optional.ofNullable;

import io.camunda.search.clients.DecisionInstanceSearchClient;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.query.DecisionInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.result.DecisionInstanceQueryResultConfig;
import io.camunda.security.auth.Authentication;
import io.camunda.security.auth.Authorization;
import io.camunda.service.exception.ForbiddenException;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;

public final class DecisionInstanceServices
    extends SearchQueryService<
        DecisionInstanceServices, DecisionInstanceQuery, DecisionInstanceEntity> {

  private final DecisionInstanceSearchClient decisionInstanceSearchClient;

  public DecisionInstanceServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityHandler,
      final DecisionInstanceSearchClient decisionInstanceSearchClient,
      final Authentication authentication) {
    super(brokerClient, securityHandler, authentication);
    this.decisionInstanceSearchClient = decisionInstanceSearchClient;
  }

  @Override
  public DecisionInstanceServices withAuthentication(final Authentication authentication) {
    return new DecisionInstanceServices(
        brokerClient, securityContextProvider, decisionInstanceSearchClient, authentication);
  }

  /**
   * Search for Decision Instances.
   *
   * <p>By default, evaluateInputs and evaluateOutputs are excluded from the returned Decision
   * Instances.
   */
  @Override
  public SearchQueryResult<DecisionInstanceEntity> search(final DecisionInstanceQuery query) {
    return decisionInstanceSearchClient
        .withSecurityContext(
            securityContextProvider.provideSecurityContext(
                authentication,
                Authorization.of(a -> a.decisionDefinition().readDecisionInstance())))
        .searchDecisionInstances(
            decisionInstanceSearchQuery(
                q ->
                    q.filter(query.filter())
                        .sort(query.sort())
                        .page(query.page())
                        .resultConfig(
                            ofNullable(query.resultConfig())
                                .orElseGet(this::defaultSearchResultConfig))));
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
    final var result =
        decisionInstanceSearchClient
            .withSecurityContext(securityContextProvider.provideSecurityContext(authentication))
            .searchDecisionInstances(
                decisionInstanceSearchQuery(
                    q -> q.filter(f -> f.decisionInstanceIds(decisionInstanceId))));
    final var decisionInstanceEntity =
        getSingleResultOrThrow(result, decisionInstanceId, "Decision instance");
    final var authorization = Authorization.of(a -> a.decisionDefinition().readDecisionInstance());
    if (!securityContextProvider.isAuthorized(
        decisionInstanceEntity.decisionDefinitionId(), authentication, authorization)) {
      throw new ForbiddenException(authorization);
    }
    return decisionInstanceEntity;
  }

  /**
   * The default config excludes evaluateInputs and evaluateOutputs fields. To include them, the
   * user must provide a config like
   *
   * <pre>
   *   {
   *     "includeEvaluatedInputs": true,
   *     "includeEvaluatedOutputs": true
   *   }
   * </pre>
   *
   * @return a default config
   */
  private DecisionInstanceQueryResultConfig defaultSearchResultConfig() {
    return DecisionInstanceQueryResultConfig.of(r -> r);
  }
}
