/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.search.query.SearchQueryBuilders.decisionRequirementsSearchQuery;
import static io.camunda.security.auth.Authorization.with;
import static io.camunda.security.auth.Authorization.withResourceId;
import static io.camunda.service.authorization.Authorizations.DECISION_REQUIREMENTS_READ_AUTHORIZATION;

import io.camunda.search.clients.DecisionRequirementSearchClient;
import io.camunda.search.entities.DecisionRequirementsEntity;
import io.camunda.search.query.DecisionRequirementsQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.zeebe.broker.client.api.BrokerClient;

public final class DecisionRequirementsServices
    extends SearchQueryService<
        DecisionRequirementsServices, DecisionRequirementsQuery, DecisionRequirementsEntity> {

  private final DecisionRequirementSearchClient decisionRequirementSearchClient;

  public DecisionRequirementsServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final DecisionRequirementSearchClient decisionRequirementSearchClient,
      final CamundaAuthentication authentication) {
    super(brokerClient, securityContextProvider, authentication);
    this.decisionRequirementSearchClient = decisionRequirementSearchClient;
  }

  @Override
  public DecisionRequirementsServices withAuthentication(
      final CamundaAuthentication authentication) {
    return new DecisionRequirementsServices(
        brokerClient, securityContextProvider, decisionRequirementSearchClient, authentication);
  }

  @Override
  public SearchQueryResult<DecisionRequirementsEntity> search(
      final DecisionRequirementsQuery query) {
    return decisionRequirementSearchClient
        .withSecurityContext(
            securityContextProvider.provideSecurityContext(
                authentication, with(DECISION_REQUIREMENTS_READ_AUTHORIZATION)))
        .searchDecisionRequirements(
            decisionRequirementsSearchQuery(
                q -> q.filter(query.filter()).sort(query.sort()).page(query.page())));
  }

  public String getDecisionRequirementsXml(final Long decisionRequirementsKey) {
    return getByKey(decisionRequirementsKey, true).xml();
  }

  public DecisionRequirementsEntity getByKey(final Long key) {
    return getByKey(key, false);
  }

  public DecisionRequirementsEntity getByKey(final Long key, final boolean includeXml) {
    return decisionRequirementSearchClient
        .withSecurityContext(
            securityContextProvider.provideSecurityContext(
                authentication,
                withResourceId(
                    DECISION_REQUIREMENTS_READ_AUTHORIZATION,
                    DecisionRequirementsEntity::decisionRequirementsId)))
        .getDecisionRequirementsByKey(key, includeXml);
  }
}
