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
import io.camunda.security.auth.BrokerRequestAuthorizationConverter;
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
      final ApiServicesExecutorProvider executorProvider,
      final BrokerRequestAuthorizationConverter brokerRequestAuthorizationConverter) {
    super(
        brokerClient,
        securityContextProvider,
        executorProvider,
        brokerRequestAuthorizationConverter);
    this.decisionRequirementSearchClient = decisionRequirementSearchClient;
  }

  @Override
  public SearchQueryResult<DecisionRequirementsEntity> search(
      final DecisionRequirementsQuery query,
      final CamundaAuthentication authentication,
      final String physicalTenantId) {
    return executeSearchRequest(
        () ->
            decisionRequirementSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, DECISION_REQUIREMENTS_READ_AUTHORIZATION))
                .withPhysicalTenant(physicalTenantId)
                .searchDecisionRequirements(
                    decisionRequirementsSearchQuery(
                        q -> q.filter(query.filter()).sort(query.sort()).page(query.page()))));
  }

  public DecisionRequirementsEntity getByKey(
      final Long key, final CamundaAuthentication authentication, final String physicalTenantId) {
    return getByKey(key, false, authentication, physicalTenantId);
  }

  public DecisionRequirementsEntity getByKey(
      final Long key,
      final boolean includeXml,
      final CamundaAuthentication authentication,
      final String physicalTenantId) {
    return executeSearchRequest(
        () ->
            decisionRequirementSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication,
                        Authorization.withAuthorization(
                            DECISION_REQUIREMENTS_READ_AUTHORIZATION,
                            DecisionRequirementsEntity::decisionRequirementsId)))
                .withPhysicalTenant(physicalTenantId)
                .getDecisionRequirements(key, includeXml));
  }

  public SearchQueryResult<DecisionRequirementsEntity> search(
      final Function<DecisionRequirementsQuery.Builder, ObjectBuilder<DecisionRequirementsQuery>>
          fn,
      final CamundaAuthentication authentication,
      final String physicalTenantId) {
    return search(decisionRequirementsSearchQuery(fn), authentication, physicalTenantId);
  }

  public String getDecisionRequirementsXml(
      final Long decisionRequirementsKey,
      final CamundaAuthentication authentication,
      final String physicalTenantId) {
    return getByKey(decisionRequirementsKey, true, authentication, physicalTenantId).xml();
  }
}
