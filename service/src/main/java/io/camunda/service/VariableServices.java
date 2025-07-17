/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.search.query.SearchQueryBuilders.variableSearchQuery;
import static io.camunda.security.auth.Authorization.withAuthorization;
import static io.camunda.service.authorization.Authorizations.VARIABLE_READ_AUTHORIZATION;

import io.camunda.search.clients.VariableSearchClient;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.VariableQuery;
import io.camunda.search.query.VariableQuery.Builder;
import io.camunda.security.auth.CamundaAuthentication;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.security.SecurityContextProvider;
import io.camunda.util.ObjectBuilder;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.util.function.Function;

public final class VariableServices
    extends SearchQueryService<VariableServices, VariableQuery, VariableEntity> {

  private final VariableSearchClient variableSearchClient;

  public VariableServices(
      final BrokerClient brokerClient,
      final SecurityContextProvider securityContextProvider,
      final VariableSearchClient variableSearchClient,
      final CamundaAuthentication authentication) {
    super(brokerClient, securityContextProvider, authentication);
    this.variableSearchClient = variableSearchClient;
  }

  @Override
  public VariableServices withAuthentication(final CamundaAuthentication authentication) {
    return new VariableServices(
        brokerClient, securityContextProvider, variableSearchClient, authentication);
  }

  @Override
  public SearchQueryResult<VariableEntity> search(final VariableQuery query) {
    return executeSearchRequest(
        () ->
            variableSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication, VARIABLE_READ_AUTHORIZATION))
                .searchVariables(query));
  }

  public SearchQueryResult<VariableEntity> search(
      final Function<Builder, ObjectBuilder<VariableQuery>> fn) {
    return search(variableSearchQuery(fn));
  }

  public VariableEntity getByKey(final Long key) {
    return executeSearchRequest(
        () ->
            variableSearchClient
                .withSecurityContext(
                    securityContextProvider.provideSecurityContext(
                        authentication,
                        withAuthorization(
                            VARIABLE_READ_AUTHORIZATION, VariableEntity::processDefinitionId)))
                .getVariable(key));
  }
}
