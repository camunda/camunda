/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation.result;

import static io.camunda.search.aggregation.DecisionDefinitionLatestVersionAggregation.AGGREGATION_NAME_BY_DECISION_ID;
import static io.camunda.search.aggregation.DecisionDefinitionLatestVersionAggregation.AGGREGATION_NAME_LATEST_DEFINITION;

import io.camunda.search.aggregation.result.DecisionDefinitionLatestVersionAggregationResult;
import io.camunda.search.clients.core.AggregationResult;
import io.camunda.search.clients.core.SearchQueryHit;
import io.camunda.search.clients.transformers.entity.DecisionDefinitionEntityTransformer;
import io.camunda.webapps.schema.entities.dmn.definition.DecisionDefinitionEntity;
import java.util.Map;

public class DecisionDefinitionLatestVersionAggregationResultTransformer
    implements AggregationResultTransformer<DecisionDefinitionLatestVersionAggregationResult> {

  @Override
  public DecisionDefinitionLatestVersionAggregationResult apply(
      final Map<String, AggregationResult> aggregations) {
    return new DecisionDefinitionLatestVersionAggregationResult(
        aggregations.get(AGGREGATION_NAME_BY_DECISION_ID).aggregations().values().stream()
            .flatMap(
                aggregationResult -> {
                  final var latestDefinition =
                      aggregationResult.aggregations().get(AGGREGATION_NAME_LATEST_DEFINITION);
                  return latestDefinition.hits().stream()
                      .map(SearchQueryHit::source)
                      .filter(DecisionDefinitionEntity.class::isInstance)
                      .map(DecisionDefinitionEntity.class::cast)
                      .map(new DecisionDefinitionEntityTransformer()::apply);
                })
            .toList(),
        aggregations.get(AGGREGATION_NAME_BY_DECISION_ID).endCursor());
  }
}

