/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.search.query.SearchQueryBuilders.variableSearchQuery;

import io.camunda.search.clients.VariableSearchClient;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.VariableQuery;
import io.camunda.search.query.VariableQuery.Builder;
import io.camunda.security.auth.Authentication;
import io.camunda.security.auth.Authorization;
import io.camunda.service.exception.ForbiddenException;
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
      final Authentication authentication) {
    super(brokerClient, securityContextProvider, authentication);
    this.variableSearchClient = variableSearchClient;
  }

  @Override
  public VariableServices withAuthentication(final Authentication authentication) {
    return new VariableServices(
        brokerClient, securityContextProvider, variableSearchClient, authentication);
  }

  @Override
  public SearchQueryResult<VariableEntity> search(final VariableQuery query) {
    return variableSearchClient
        .withSecurityContext(
            securityContextProvider.provideSecurityContext(
                authentication, Authorization.of(a -> a.processDefinition().readInstance())))
        .searchVariables(query);
  }

  public SearchQueryResult<VariableEntity> search(
      final Function<Builder, ObjectBuilder<VariableQuery>> fn) {
    return search(variableSearchQuery(fn));
  }

  public VariableEntity getByKey(final Long key) {
    final var result =
        variableSearchClient
            .withSecurityContext(securityContextProvider.provideSecurityContext(authentication))
            .searchVariables(variableSearchQuery(q -> q.filter(f -> f.variableKeys(key))));
    final var variableEntity = getSingleResultOrThrow(result, key, "Variable");
    final var authorization = Authorization.of(a -> a.processDefinition().readInstance());
    if (!securityContextProvider.isAuthorized(
        variableEntity.bpmnProcessId(), authentication, authorization)) {
      throw new ForbiddenException(authorization);
    }
    return variableEntity;
  }
}
