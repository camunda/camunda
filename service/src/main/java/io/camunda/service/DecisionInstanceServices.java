/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.service.search.query.SearchQueryBuilders.decisionInstanceSearchQuery;

import io.camunda.search.clients.DecisionInstanceSearchClient;
import io.camunda.service.entities.DecisionInstanceEntity;
import io.camunda.service.exception.SearchQueryExecutionException;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.search.query.DecisionInstanceQuery;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.security.auth.Authentication;
import io.camunda.util.ObjectBuilder;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.util.function.Function;

public final class DecisionInstanceServices
    extends SearchQueryService<
        DecisionInstanceServices, DecisionInstanceQuery, DecisionInstanceEntity> {

  private final DecisionInstanceSearchClient decisionInstanceSearchClient;

  public DecisionInstanceServices(
      final BrokerClient brokerClient,
      final DecisionInstanceSearchClient decisionInstanceSearchClient,
      final Authentication authentication) {
    super(brokerClient, authentication);

    this.decisionInstanceSearchClient = decisionInstanceSearchClient;
  }

  @Override
  public DecisionInstanceServices withAuthentication(final Authentication authentication) {
    return new DecisionInstanceServices(brokerClient, decisionInstanceSearchClient, authentication);
  }

  @Override
  public SearchQueryResult<DecisionInstanceEntity> search(final DecisionInstanceQuery query) {
    return decisionInstanceSearchClient
        .searchDecisionInstances(query, authentication)
        .fold(
            (e) -> {
              throw new SearchQueryExecutionException("Failed to execute search query", e);
            },
            (r) -> r);
  }

  public SearchQueryResult<DecisionInstanceEntity> search(
      final Function<DecisionInstanceQuery.Builder, ObjectBuilder<DecisionInstanceQuery>> fn) {
    return search(decisionInstanceSearchQuery(fn));
  }
}
