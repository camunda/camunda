/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation.result;

import static io.camunda.search.aggregation.ProcessDefinitionLatestVersionAggregation.AGGREGATION_NAME_BY_PROCESS_ID;
import static io.camunda.search.aggregation.ProcessDefinitionLatestVersionAggregation.AGGREGATION_NAME_LATEST_DEFINITION;

import io.camunda.search.aggregation.result.ProcessDefinitionLatestVersionAggregationResult;
import io.camunda.search.clients.core.AggregationResult;
import io.camunda.search.clients.core.SearchQueryHit;
import io.camunda.search.clients.transformers.entity.ProcessDefinitionEntityTransfomer;
import io.camunda.webapps.schema.entities.ProcessEntity;
import java.util.Map;

public class ProcessDefinitionLatestVersionAggregationResultTransformer
    implements AggregationResultTransformer<ProcessDefinitionLatestVersionAggregationResult> {

  @Override
  public ProcessDefinitionLatestVersionAggregationResult apply(
      final Map<String, AggregationResult> aggregations) {
    return new ProcessDefinitionLatestVersionAggregationResult(
        aggregations.get(AGGREGATION_NAME_BY_PROCESS_ID).aggregations().values().stream()
            .flatMap(
                aggregationResult -> {
                  final var latestDefinition =
                      aggregationResult.aggregations().get(AGGREGATION_NAME_LATEST_DEFINITION);
                  return latestDefinition.hits().stream()
                      .map(SearchQueryHit::source)
                      .filter(ProcessEntity.class::isInstance)
                      .map(ProcessEntity.class::cast)
                      .map(new ProcessDefinitionEntityTransfomer()::apply);
                })
            .toList());
  }
}
