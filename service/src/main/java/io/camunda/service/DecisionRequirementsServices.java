/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.search.query.SearchQueryBuilders.decisionRequirementsSearchQuery;

import io.camunda.search.clients.DecisionRequirementSearchClient;
import io.camunda.search.entities.DecisionRequirementsEntity;
import io.camunda.search.exception.CamundaSearchException;
import io.camunda.search.exception.NotFoundException;
import io.camunda.search.query.DecisionRequirementsQuery;
import io.camunda.search.query.SearchQueryResult;
import io.camunda.search.security.auth.Authentication;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.util.ObjectBuilder;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.util.function.Function;

public final class DecisionRequirementsServices
    extends SearchQueryService<
        DecisionRequirementsServices, DecisionRequirementsQuery, DecisionRequirementsEntity> {

  private final DecisionRequirementSearchClient decisionRequirementSearchClient;

  public DecisionRequirementsServices(
      final BrokerClient brokerClient,
      final DecisionRequirementSearchClient decisionRequirementSearchClient,
      final Authentication authentication) {
    super(brokerClient, authentication);
    this.decisionRequirementSearchClient = decisionRequirementSearchClient;
  }

  @Override
  public DecisionRequirementsServices withAuthentication(final Authentication authentication) {
    return new DecisionRequirementsServices(
        brokerClient, decisionRequirementSearchClient, authentication);
  }

  @Override
  public SearchQueryResult<DecisionRequirementsEntity> search(
      final DecisionRequirementsQuery query) {
    return decisionRequirementSearchClient.searchDecisionRequirements(query, authentication);
  }

  public DecisionRequirementsEntity getByKey(final Long key) {
    final SearchQueryResult<DecisionRequirementsEntity> result =
        search(
            decisionRequirementsSearchQuery().filter(f -> f.decisionRequirementsKeys(key)).build());
    if (result.total() < 1) {
      throw new NotFoundException(
          String.format("Decision requirements with decisionRequirementsKey=%d not found", key));
    } else if (result.total() > 1) {
      throw new CamundaSearchException(
          String.format("Found decision requirements with key %d more than once", key));
    } else {
      return result.items().stream().findFirst().orElseThrow();
    }
  }

  public SearchQueryResult<DecisionRequirementsEntity> search(
      final Function<DecisionRequirementsQuery.Builder, ObjectBuilder<DecisionRequirementsQuery>>
          fn) {
    return search(decisionRequirementsSearchQuery(fn));
  }

  public String getDecisionRequirementsXml(final Long decisionRequirementsKey) {
    final var decisionRequirementsQuery =
        decisionRequirementsSearchQuery(
            q ->
                q.filter(f -> f.decisionRequirementsKeys(decisionRequirementsKey))
                    .resultConfig(r -> r.xml().include()));
    return search(decisionRequirementsQuery).items().stream()
        .findFirst()
        .map(DecisionRequirementsEntity::xml)
        .orElseThrow(
            () ->
                new NotFoundException(
                    "Decision Requirements with decisionRequirementsKey=%d cannot be found"
                        .formatted(decisionRequirementsKey)));
  }
}
