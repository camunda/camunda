/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.service.search.query.SearchQueryBuilders.decisionInstanceSearchQuery;
import static java.util.Optional.ofNullable;

import io.camunda.search.clients.CamundaSearchClient;
import io.camunda.service.entities.DecisionInstanceEntity;
import io.camunda.service.exception.NotFoundException;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.search.query.DecisionInstanceQuery;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.search.result.DecisionInstanceQueryResultConfig;
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

  /**
   * Search for Decision Instances.
   *
   * <p>By default, evaluateInputs and evaluateOutputs are excluded from the returned Decision
   * Instances.
   */
  @Override
  public SearchQueryResult<DecisionInstanceEntity> search(final DecisionInstanceQuery query) {
    return baseSearch(
        q ->
            q.filter(query.filter())
                .sort(query.sort())
                .page(query.page())
                .resultConfig(
                    ofNullable(query.resultConfig()).orElseGet(() -> defaultSearchResultConfig())));
  }

  /**
   * Get a Decision Instance by its key.
   *
   * @param decisionInstanceKey the key of the Decision Instance
   * @return the Decision Instance
   * @throws NotFoundException if the Decision Instance with the given key does not exist
   * @throws CamundaServiceException if the Decision Instance with the given key exists more than
   *     once
   */
  public DecisionInstanceEntity getByKey(final long decisionInstanceKey) {
    final var result = baseSearch(q -> q.filter(f -> f.decisionInstanceKeys(decisionInstanceKey)));
    if (result.total() < 1) {
      throw new NotFoundException(
          "Decision Instance with decisionInstanceKey=%d not found".formatted(decisionInstanceKey));
    } else if (result.total() > 1) {
      throw new CamundaServiceException(
          String.format(
              "Found Decision Definition with key %d more than once", decisionInstanceKey));
    } else {
      return result.items().stream().findFirst().orElseThrow();
    }
  }

  private SearchQueryResult<DecisionInstanceEntity> baseSearch(
      final Function<DecisionInstanceQuery.Builder, ObjectBuilder<DecisionInstanceQuery>> fn) {
    return executor.search(decisionInstanceSearchQuery(fn), DecisionInstanceEntity.class);
  }

  private DecisionInstanceQueryResultConfig defaultSearchResultConfig() {
    return DecisionInstanceQueryResultConfig.of(
        r -> r.evaluatedInputs().exclude().evaluatedOutputs().exclude());
  }
}
