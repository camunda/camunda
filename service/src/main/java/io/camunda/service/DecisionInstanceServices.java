/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.security.auth.Authorization.with;
import static io.camunda.security.auth.Authorization.withResourceId;
import static io.camunda.service.authorization.Authorizations.DECISION_INSTANCE_READ_AUTHORIZATION;

import io.camunda.search.clients.DecisionInstanceSearchClient;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.query.DecisionInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.CamundaAuthentication;
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
      final CamundaAuthentication authentication) {
    super(brokerClient, securityHandler, authentication);
    this.decisionInstanceSearchClient = decisionInstanceSearchClient;
  }

  @Override
  public DecisionInstanceServices withAuthentication(final CamundaAuthentication authentication) {
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
                authentication, with(DECISION_INSTANCE_READ_AUTHORIZATION)))
        .searchDecisionInstances(query);
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
    return decisionInstanceSearchClient
        .withSecurityContext(
            securityContextProvider.provideSecurityContext(
                authentication,
                withResourceId(
                    DECISION_INSTANCE_READ_AUTHORIZATION,
                    DecisionInstanceEntity::decisionDefinitionId)))
        .getDecisionInstanceById(decisionInstanceId);
  }
}
