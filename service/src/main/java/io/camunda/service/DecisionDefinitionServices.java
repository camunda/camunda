/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.service;

import static io.camunda.service.search.query.SearchQueryBuilders.decisionDefinitionSearchQuery;
import static io.camunda.service.search.query.SearchQueryBuilders.decisionRequirementsSearchQuery;

import io.camunda.search.clients.CamundaSearchClient;
import io.camunda.service.entities.DecisionDefinitionEntity;
import io.camunda.service.entities.DecisionRequirementsEntity;
import io.camunda.service.exception.NotFoundException;
import io.camunda.service.search.core.SearchQueryService;
import io.camunda.service.search.query.DecisionDefinitionQuery;
import io.camunda.service.search.query.SearchQueryResult;
import io.camunda.service.security.auth.Authentication;
import io.camunda.service.transformers.ServiceTransformers;
import io.camunda.util.ObjectBuilder;
import io.camunda.zeebe.broker.client.api.BrokerClient;
import java.util.function.Function;

public final class DecisionDefinitionServices
    extends SearchQueryService<
        DecisionDefinitionServices, DecisionDefinitionQuery, DecisionDefinitionEntity> {

  public DecisionDefinitionServices(
      final BrokerClient brokerClient, final CamundaSearchClient dataStoreClient) {
    this(brokerClient, dataStoreClient, null, null);
  }

  public DecisionDefinitionServices(
      final BrokerClient brokerClient,
      final CamundaSearchClient searchClient,
      final ServiceTransformers transformers,
      final Authentication authentication) {
    super(brokerClient, searchClient, transformers, authentication);
  }

  @Override
  public DecisionDefinitionServices withAuthentication(final Authentication authentication) {
    return new DecisionDefinitionServices(brokerClient, searchClient, transformers, authentication);
  }

  @Override
  public SearchQueryResult<DecisionDefinitionEntity> search(final DecisionDefinitionQuery query) {
    return executor.search(query, DecisionDefinitionEntity.class);
  }

  public SearchQueryResult<DecisionDefinitionEntity> search(
      final Function<DecisionDefinitionQuery.Builder, ObjectBuilder<DecisionDefinitionQuery>> fn) {
    return search(decisionDefinitionSearchQuery(fn));
  }

  public String getDecisionDefinitionXml(final Long decisionKey) {
    final var decisionDefinitionQuery =
        decisionDefinitionSearchQuery(q -> q.filter(f -> f.decisionKeys(decisionKey)));
    final var decisionDefinition =
        search(decisionDefinitionQuery).items().stream()
            .findFirst()
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "DecisionDefinition with decisionKey=%d cannot be found"
                            .formatted(decisionKey)));

    final Long decisionRequirementsKey = decisionDefinition.decisionRequirementsKey();
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
                    "DecisionRequirements with decisionRequirementsKey=%d cannot be found"
                        .formatted(decisionRequirementsKey)));
  }
}
