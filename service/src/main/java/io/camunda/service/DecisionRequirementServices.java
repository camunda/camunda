/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import io.camunda.search.clients.CamundaSearchClient;
import io.camunda.service.entities.DecisionRequirementEntity;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.search.query.DecisionRequirementQuery;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.security.auth.Authentication;
import io.camunda.service.transformers.ServiceTransformers;
import io.camunda.util.ObjectBuilder;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.util.function.Function;

public final class DecisionRequirementServices
    extends SearchQueryService<
        DecisionRequirementServices, DecisionRequirementQuery, DecisionRequirementEntity> {

  public DecisionRequirementServices(
      final BrokerClient brokerClient, final CamundaSearchClient dataStoreClient) {
    this(brokerClient, dataStoreClient, null, null);
  }

  public DecisionRequirementServices(
      final BrokerClient brokerClient,
      final CamundaSearchClient searchClient,
      final ServiceTransformers transformers,
      final Authentication authentication) {
    super(brokerClient, searchClient, transformers, authentication);
  }

  @Override
  public DecisionRequirementServices withAuthentication(final Authentication authentication) {
    return new DecisionRequirementServices(
        brokerClient, searchClient, transformers, authentication);
  }

  @Override
  public SearchQueryResult<DecisionRequirementEntity> search(final DecisionRequirementQuery query) {
    return executor.search(query, DecisionRequirementEntity.class);
  }

  public SearchQueryResult<DecisionRequirementEntity> search(
      final Function<DecisionRequirementQuery.Builder, ObjectBuilder<DecisionRequirementQuery>>
          fn) {
    return search(SearchQueryBuilders.decisionRequirementSearchQuery(fn));
  }
}
