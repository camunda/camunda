/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.service.search.query.SearchQueryBuilders.decisionRequirementsSearchQuery;

import io.camunda.search.clients.CamundaSearchClient;
import io.camunda.service.entities.DecisionRequirementsEntity;
import io.camunda.service.exception.NotFoundException;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.search.query.DecisionRequirementsQuery;
import io.camunda.service.search.query.SearchQueryBuilders;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.security.auth.Authentication;
import io.camunda.service.transformers.ServiceTransformers;
import io.camunda.util.ObjectBuilder;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.util.function.Function;

public final class DecisionRequirementsServices
    extends SearchQueryService<
        DecisionRequirementsServices, DecisionRequirementsQuery, DecisionRequirementsEntity> {

  public DecisionRequirementsServices(
      final BrokerClient brokerClient, final CamundaSearchClient dataStoreClient) {
    this(brokerClient, dataStoreClient, null, null);
  }

  public DecisionRequirementsServices(
      final BrokerClient brokerClient,
      final CamundaSearchClient searchClient,
      final ServiceTransformers transformers,
      final Authentication authentication) {
    super(brokerClient, searchClient, transformers, authentication);
  }

  public DecisionRequirementsEntity getByKey(final Long key) {
    final SearchQueryResult<DecisionRequirementsEntity> result =
        executor.search(
            SearchQueryBuilders.decisionRequirementsSearchQuery()
                .filter(f -> f.decisionRequirementsKeys(key))
                .build(),
            DecisionRequirementsEntity.class);
    if (result.total() < 1) {
      throw new NotFoundException(
          String.format("Decision requirements with key %d not found", key));
    } else if (result.total() > 1) {
      throw new CamundaServiceException(
          String.format("Found decision requirements with key %d more than once", key));
    } else {
      return result.items().stream().findFirst().orElseThrow();
    }
  }

  @Override
  public DecisionRequirementsServices withAuthentication(final Authentication authentication) {
    return new DecisionRequirementsServices(
        brokerClient, searchClient, transformers, authentication);
  }

  @Override
  public SearchQueryResult<DecisionRequirementsEntity> search(
      final DecisionRequirementsQuery query) {
    return executor.search(query, DecisionRequirementsEntity.class);
  }

  public SearchQueryResult<DecisionRequirementsEntity> search(
      final Function<DecisionRequirementsQuery.Builder, ObjectBuilder<DecisionRequirementsQuery>>
          fn) {
    return search(SearchQueryBuilders.decisionRequirementsSearchQuery(fn));
  }

  public String getDecisionRequirementsXml(final Long decisionRequirementsKey) {
    final var decisionRequirementsQuery =
        decisionRequirementsSearchQuery(
            q ->
                q.filter(f -> f.decisionRequirementsKeys(decisionRequirementsKey))
                    .resultConfig(r -> r.xml().include()));
    return executor
        .search(decisionRequirementsQuery, DecisionRequirementsEntity.class)
        .items()
        .stream()
        .findFirst()
        .map(DecisionRequirementsEntity::xml)
        .orElseThrow(
            () ->
                new NotFoundException(
                    "Decision Requirements with decisionRequirementsKey=%d cannot be found"
                        .formatted(decisionRequirementsKey)));
  }
}
