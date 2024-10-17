/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.search.clients.VariableSearchClient;
import io.camunda.search.entities.VariableEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.exception.NotFoundException;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.query.VariableQuery;
import io.camunda.search.query.VariableQuery.Builder;
import io.camunda.search.security.auth.Authentication;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.util.ObjectBuilder;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.util.function.Function;

public final class VariableServices
    extends SearchQueryService<VariableServices, VariableQuery, VariableEntity> {

  private final VariableSearchClient variableSearchClient;

  public VariableServices(
      final BrokerClient brokerClient,
      final VariableSearchClient variableSearchClient,
      final Authentication authentication) {
    super(brokerClient, authentication);
    this.variableSearchClient = variableSearchClient;
  }

  @Override
  public VariableServices withAuthentication(final Authentication authentication) {
    return new VariableServices(brokerClient, variableSearchClient, authentication);
  }

  @Override
  public SearchQueryResult<VariableEntity> search(final VariableQuery query) {
    return variableSearchClient.searchVariables(query, authentication);
  }

  public SearchQueryResult<VariableEntity> search(
      final Function<Builder, ObjectBuilder<VariableQuery>> fn) {
    return search(SearchQueryBuilders.variableSearchQuery(fn));
  }

  public VariableEntity getByKey(final Long key) {
    final SearchQueryResult<VariableEntity> result =
        search(SearchQueryBuilders.variableSearchQuery().filter(f -> f.variableKeys(key)).build());
    if (result.total() < 1) {
      throw new NotFoundException(String.format("Variable with key %d not found", key));
    } else if (result.total() > 1) {
      throw new CamundaSearchException(
          String.format("Found Variable with key %d more than once", key));
    } else {
      return result.items().stream().findFirst().orElseThrow();
    }
  }
}
