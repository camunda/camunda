/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.search.query.SearchQueryBuilders.decisionRequirementsSearchQuery;
import static io.camunda.service.authorization.Authorizations.DECISION_REQUIREMENTS_READ_AUTHORIZATION;

import io.camunda.search.clients.DecisionRequirementSearchClient;
import io.camunda.search.entities.DecisionRequirementsEntity;
import io.camunda.search.query.DecisionRequirementsQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.util.ObjectBuilder;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.util.function.Function;

public final class DecisionRequirementsServices
    extends SearchQueryService<
        DecisionRequirementsServices, DecisionRequirementsQuery, DecisionRequirementsEntity> {

  private final DecisionRequirementSearchClient decisionRequirementSearchClient;

  public DecisionRequirementsServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final DecisionRequirementSearchClient decisionRequirementSearchClient,
      final CamundaAuthentication authentication,
      final ApiServicesExecutorProvider executorProvider) {
    super(brokerClient, securityContextProvider, authentication, executorProvider);
    this.decisionRequirementSearchClient = decisionRequirementSearchClient;
  }

  @Override
  public DecisionRequirementsServices withAuthentication(
      final CamundaAuthentication authentication) {
    return new DecisionRequirementsServices(
        brokerClient,
        securityContextProvider,
        decisionRequirementSearchClient,
        authentication,
        executorProvider);
  }

  @Override
  public SearchQueryResult<DecisionRequirementsEntity> search(
      final DecisionRequirementsQuery query) {
    return executeSearchRequest(
        () ->
            decisionRequirementSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, DECISION_REQUIREMENTS_READ_AUTHORIZATION))
                .searchDecisionRequirements(
                    decisionRequirementsSearchQuery(
                        q -> q.filter(query.filter()).sort(query.sort()).page(query.page()))));
  }

  public DecisionRequirementsEntity getByKey(final Long key) {
    return getByKey(key, false);
  }

  public DecisionRequirementsEntity getByKey(final Long key, final boolean includeXml) {
    return executeSearchRequest(
        () ->
            decisionRequirementSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication,
                        Authorization.withAuthorization(
                            DECISION_REQUIREMENTS_READ_AUTHORIZATION,
                            DecisionRequirementsEntity::decisionRequirementsId)))
                .getDecisionRequirements(key, includeXml));
  }

  public SearchQueryResult<DecisionRequirementsEntity> search(
      final Function<DecisionRequirementsQuery.Builder, ObjectBuilder<DecisionRequirementsQuery>>
          fn) {
    return search(decisionRequirementsSearchQuery(fn));
  }

  public String getDecisionRequirementsXml(final Long decisionRequirementsKey) {
    return getByKey(decisionRequirementsKey, true).xml();
  }
}
