/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.search.clients.CamundaSearchClient;
import io.camunda.service.entities.FlownodeInstanceEntity;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.search.query.FlownodeInstanceQuery;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.security.auth.Authentication;
import io.camunda.service.transformers.ServiceTransformers;
import io.camunda.util.ObjectBuilder;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.util.function.Function;

public final class FlownodeInstanceServices
    extends SearchQueryService<
        FlownodeInstanceServices, FlownodeInstanceQuery, FlownodeInstanceEntity> {

  public FlownodeInstanceServices(
      final BrokerClient brokerClient, final CamundaSearchClient dataStoreClient) {
    this(brokerClient, dataStoreClient, null, null);
  }

  public FlownodeInstanceServices(
      final BrokerClient brokerClient,
      final CamundaSearchClient searchClient,
      final ServiceTransformers transformers,
      final Authentication authentication) {
    super(brokerClient, searchClient, transformers, authentication);
  }

  @Override
  public FlownodeInstanceServices withAuthentication(final Authentication authentication) {
    return new FlownodeInstanceServices(brokerClient, searchClient, transformers, authentication);
  }

  @Override
  public SearchQueryResult<FlownodeInstanceEntity> search(final FlownodeInstanceQuery query) {
    return executor.search(query, FlownodeInstanceEntity.class);
  }

  public SearchQueryResult<FlownodeInstanceEntity> search(
      final Function<FlownodeInstanceQuery.Builder, ObjectBuilder<FlownodeInstanceQuery>> fn) {
    return search(SearchQueryBuilders.flownodeInstanceSearchQuery(fn));
  }
}
