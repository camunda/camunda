/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.search.clients.CamundaSearchClient;
import io.camunda.service.entities.VariableEntity;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.search.query.VariableQuery;
import io.camunda.service.search.query.VariableQuery.Builder;
import io.camunda.service.security.auth.Authentication;
import io.camunda.service.transformers.ServiceTransformers;
import io.camunda.util.ObjectBuilder;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.util.function.Function;

public final class VariableServices
    extends SearchQueryService<VariableServices, VariableQuery, VariableEntity> {

  public VariableServices(
      final BrokerClient brokerClient, final CamundaSearchClient dataStoreClient) {
    this(brokerClient, dataStoreClient, null, null);
  }

  public VariableServices(
      final BrokerClient brokerClient,
      final CamundaSearchClient searchClient,
      final ServiceTransformers transformers,
      final Authentication authentication) {
    super(brokerClient, searchClient, transformers, authentication);
  }

  @Override
  public VariableServices withAuthentication(final Authentication authentication) {
    return new VariableServices(brokerClient, searchClient, transformers, authentication);
  }

  @Override
  public SearchQueryResult<VariableEntity> search(final VariableQuery query) {
    return executor.search(query, VariableEntity.class);
  }

  public SearchQueryResult<VariableEntity> search(
      final Function<Builder, ObjectBuilder<VariableQuery>> fn) {
    return search(SearchQueryBuilders.variableSearchQuery(fn));
  }
}
