/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.search.clients.CamundaSearchClient;
import io.camunda.service.entities.FlowNodeInstanceEntity;
import io.camunda.service.exception.NotFoundException;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.search.query.FlowNodeInstanceQuery;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.security.auth.Authentication;
import io.camunda.service.transformers.ServiceTransformers;
import io.camunda.util.ObjectBuilder;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.util.function.Function;

public final class FlowNodeInstanceServices
    extends SearchQueryService<
        FlowNodeInstanceServices, FlowNodeInstanceQuery, FlowNodeInstanceEntity> {

  public FlowNodeInstanceServices(
      final BrokerClient brokerClient, final CamundaSearchClient dataStoreClient) {
    this(brokerClient, dataStoreClient, null, null);
  }

  public FlowNodeInstanceServices(
      final BrokerClient brokerClient,
      final CamundaSearchClient searchClient,
      final ServiceTransformers transformers,
      final Authentication authentication) {
    super(brokerClient, searchClient, transformers, authentication);
  }

  @Override
  public FlowNodeInstanceServices withAuthentication(final Authentication authentication) {
    return new FlowNodeInstanceServices(brokerClient, searchClient, transformers, authentication);
  }

  @Override
  public SearchQueryResult<FlowNodeInstanceEntity> search(final FlowNodeInstanceQuery query) {
    return executor.search(query, FlowNodeInstanceEntity.class);
  }

  public SearchQueryResult<FlowNodeInstanceEntity> search(
      final Function<FlowNodeInstanceQuery.Builder, ObjectBuilder<FlowNodeInstanceQuery>> fn) {
    return search(SearchQueryBuilders.flownodeInstanceSearchQuery(fn));
  }

  public FlowNodeInstanceEntity getByKey(final Long key) {
    final SearchQueryResult<FlowNodeInstanceEntity> result =
        executor.search(
            SearchQueryBuilders.flownodeInstanceSearchQuery()
                .filter(f -> f.flowNodeInstanceKeys(key))
                .build(),
            FlowNodeInstanceEntity.class);
    if (result.total() < 1) {
      throw new NotFoundException(String.format("Flow node instance with key %d not found", key));
    } else if (result.total() > 1) {
      throw new CamundaServiceException(
          String.format("Found Flow node instance with key %d more than once", key));
    } else {
      return result.items().stream().findFirst().orElseThrow();
    }
  }
}
