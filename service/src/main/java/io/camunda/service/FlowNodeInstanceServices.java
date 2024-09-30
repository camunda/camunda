/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.search.clients.FlowNodeInstanceSearchClient;
import io.camunda.search.entities.FlowNodeInstanceEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.exception.NotFoundException;
import io.camunda.search.query.FlowNodeInstanceQuery;
import io.camunda.search.query.SearchQueryBuilders;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.security.auth.Authentication;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.util.ObjectBuilder;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.util.function.Function;

public final class FlowNodeInstanceServices
    extends SearchQueryService<
        FlowNodeInstanceServices, FlowNodeInstanceQuery, FlowNodeInstanceEntity> {

  private final FlowNodeInstanceSearchClient flowNodeInstanceSearchClient;

  public FlowNodeInstanceServices(
      final BrokerClient brokerClient,
      final FlowNodeInstanceSearchClient flowNodeInstanceSearchClient,
      final Authentication authentication) {
    super(brokerClient, authentication);
    this.flowNodeInstanceSearchClient = flowNodeInstanceSearchClient;
  }

  @Override
  public FlowNodeInstanceServices withAuthentication(final Authentication authentication) {
    return new FlowNodeInstanceServices(brokerClient, flowNodeInstanceSearchClient, authentication);
  }

  @Override
  public SearchQueryResult<FlowNodeInstanceEntity> search(final FlowNodeInstanceQuery query) {
    return flowNodeInstanceSearchClient.searchFlowNodeInstances(query, authentication);
  }

  public SearchQueryResult<FlowNodeInstanceEntity> search(
      final Function<FlowNodeInstanceQuery.Builder, ObjectBuilder<FlowNodeInstanceQuery>> fn) {
    return search(SearchQueryBuilders.flownodeInstanceSearchQuery(fn));
  }

  public FlowNodeInstanceEntity getByKey(final Long key) {
    final SearchQueryResult<FlowNodeInstanceEntity> result =
        search(
            SearchQueryBuilders.flownodeInstanceSearchQuery()
                .filter(f -> f.flowNodeInstanceKeys(key))
                .build());
    if (result.total() < 1) {
      throw new NotFoundException(String.format("Flow node instance with key %d not found", key));
    } else if (result.total() > 1) {
      throw new CamundaSearchException(
          String.format("Found Flow node instance with key %d more than once", key));
    } else {
      return result.items().stream().findFirst().orElseThrow();
    }
  }
}
