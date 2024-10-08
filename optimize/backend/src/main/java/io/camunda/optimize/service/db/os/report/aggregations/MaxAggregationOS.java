/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.aggregations;

import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import io.camunda.optimize.service.db.report.interpreter.util.AggregationResultMappingUtil;
import java.util.Map;
import org.apache.commons.lang3.tuple.Pair;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.MaxAggregate;
import org.opensearch.client.opensearch._types.aggregations.MaxAggregation;

public class MaxAggregationOS extends AggregationStrategyOS {
  private static final String MAX_AGGREGATION = "maxAggregation";

  @Override
  public AggregationDto getAggregationType() {
    return new AggregationDto(AggregationType.MAX);
  }

  @Override
  public Double getValueForAggregation(
      final String customIdentifier, final Map<String, Aggregate> aggs) {
    final MaxAggregate aggregate =
        aggs.get(createAggregationName(customIdentifier, MAX_AGGREGATION)).max();
    return getValue(aggregate.value(), aggregate.meta());
  }

  @Override
  public Pair<String, Aggregation> createAggregation(
      final String customIdentifier, final Script script, final String... fields) {
    final MaxAggregation.Builder builder = new MaxAggregation.Builder().script(script);
    AggregationResultMappingUtil.firstField(fields).ifPresent(builder::field);
    return Pair.of(
        createAggregationName(customIdentifier, MAX_AGGREGATION), builder.build()._toAggregation());
  }
}
