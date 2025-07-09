/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.search.query.SearchQueryBuilders.decisionRequirementsSearchQuery;

import io.camunda.search.clients.DecisionRequirementSearchClient;
import io.camunda.search.entities.DecisionRequirementsEntity;
import io.camunda.search.query.DecisionRequirementsQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.security.auth.Authorization;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.exception.ForbiddenException;
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
                authentication, Authorization.of(a -> a.decisionRequirementsDefinition().read())))
        .searchDecisionRequirements(
            decisionRequirementsSearchQuery(
                q -> q.filter(query.filter()).sort(query.sort()).page(query.page())));
  }

  public DecisionRequirementsEntity getByKey(final Long key) {
    return getByKey(key, false);
  }

  public DecisionRequirementsEntity getByKey(final Long key, final boolean includeXml) {
    final var result =
        decisionRequirementSearchClient
            .withSecurityContext(securityContextProvider.provideSecurityContext(authentication))
            .searchDecisionRequirements(
                decisionRequirementsSearchQuery(
                    q ->
                        q.filter(f -> f.decisionRequirementsKeys(key))
                            .resultConfig(r -> r.includeXml(includeXml))
                            .singleResult()))
            .items()
            .getFirst();
    final var authorization = Authorization.of(a -> a.decisionRequirementsDefinition().read());
    if (!securityContextProvider.isAuthorized(
        result.decisionRequirementsId(), authentication, authorization)) {
      throw new ForbiddenException(authorization);
    }
    return result;
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
