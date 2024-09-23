/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.service.search.query.SearchQueryBuilders.decisionInstanceSearchQuery;

import io.camunda.search.clients.CamundaSearchClient;
import io.camunda.service.entities.DecisionInstanceEntity;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.search.query.DecisionInstanceQuery;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.security.auth.Authentication;
import io.camunda.service.transformers.ServiceTransformers;
import io.camunda.util.ObjectBuilder;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.util.function.Function;

public final class DecisionInstanceServices
    extends SearchQueryService<
        DecisionInstanceServices, DecisionInstanceQuery, DecisionInstanceEntity> {

  public DecisionInstanceServices(
      final BrokerClient brokerClient, final CamundaSearchClient dataStoreClient) {
    this(brokerClient, dataStoreClient, null, null);
  }

  public DecisionInstanceServices(
      final BrokerClient brokerClient,
      final CamundaSearchClient searchClient,
      final ServiceTransformers transformers,
      final Authentication authentication) {
    super(brokerClient, searchClient, transformers, authentication);
  }

  @Override
  public DecisionInstanceServices withAuthentication(final Authentication authentication) {
    return new DecisionInstanceServices(brokerClient, searchClient, transformers, authentication);
  }

  @Override
  public SearchQueryResult<DecisionInstanceEntity> search(final DecisionInstanceQuery query) {
    return executor.search(query, DecisionInstanceEntity.class);
  }

  public SearchQueryResult<DecisionInstanceEntity> search(
      final Function<DecisionInstanceQuery.Builder, ObjectBuilder<DecisionInstanceQuery>> fn) {
    return search(decisionInstanceSearchQuery(fn));
  }
}
