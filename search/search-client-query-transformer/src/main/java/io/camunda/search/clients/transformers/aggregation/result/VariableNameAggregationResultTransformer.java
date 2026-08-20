/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.search.clients.transformers.aggregation.result;

import static io.camunda.search.aggregation.VariableNameAggregation.AGGREGATION_NAME_BY_NAME;

import io.camunda.search.aggregation.result.VariableNameAggregationResult;
import io.camunda.search.clients.core.AggregationResult;
import java.util.List;
import java.util.Map;

public class VariableNameAggregationResultTransformer
    implements AggregationResultTransformer<VariableNameAggregationResult> {

  @Override
  public VariableNameAggregationResult apply(final Map<String, AggregationResult> aggregations) {
    final var byNameAgg = aggregations.get(AGGREGATION_NAME_BY_NAME);
    if (byNameAgg == null || byNameAgg.aggregations() == null) {
      return new VariableNameAggregationResult(List.of());
    }
    return new VariableNameAggregationResult(List.copyOf(byNameAgg.aggregations().keySet()));
  }
}
