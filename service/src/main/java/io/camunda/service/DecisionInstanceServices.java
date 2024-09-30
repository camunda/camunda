/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.search.query.SearchQueryBuilders.decisionInstanceSearchQuery;
import static java.util.Optional.ofNullable;

import io.camunda.search.clients.DecisionInstanceSearchClient;
import io.camunda.search.entities.DecisionInstanceEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.exception.NotFoundException;
import io.camunda.search.query.DecisionInstanceQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.result.DecisionInstanceQueryResultConfig;
import io.camunda.search.security.auth.Authentication;
import io.camunda.service.search.core.SearchQueryService;
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
   * @throws CamundaSearchException if the Decision Instance with the given key exists more than
   *     once
   */
  public DecisionInstanceEntity getByKey(final long decisionInstanceKey) {
    final var result = baseSearch(q -> q.filter(f -> f.decisionInstanceKeys(decisionInstanceKey)));
    if (result.total() < 1) {
      throw new NotFoundException(
          "Decision Instance with decisionInstanceKey=%d not found".formatted(decisionInstanceKey));
    } else if (result.total() > 1) {
      throw new CamundaSearchException(
          String.format(
              "Found Decision Definition with key %d more than once", decisionInstanceKey));
    } else {
      return result.items().stream().findFirst().orElseThrow();
    }
  }

  private SearchQueryResult<DecisionInstanceEntity> baseSearch(
      final Function<DecisionInstanceQuery.Builder, ObjectBuilder<DecisionInstanceQuery>> fn) {
    return decisionInstanceSearchClient.searchDecisionInstances(
        decisionInstanceSearchQuery(fn), authentication);
  }

  private DecisionInstanceQueryResultConfig defaultSearchResultConfig() {
    return DecisionInstanceQueryResultConfig.of(
        r -> r.evaluatedInputs().exclude().evaluatedOutputs().exclude());
  }
}
