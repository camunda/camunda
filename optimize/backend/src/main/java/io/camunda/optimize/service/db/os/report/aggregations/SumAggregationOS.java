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
import org.opensearch.client.opensearch._types.aggregations.SumAggregate;
import org.opensearch.client.opensearch._types.aggregations.SumAggregation;

public class SumAggregationOS extends AggregationStrategyOS {
  private static final String SUM_AGGREGATION = "sumAggregation";

  @Override
  public AggregationDto getAggregationType() {
    return new AggregationDto(AggregationType.SUM);
  }

  @Override
  public Double getValueForAggregation(
      final String customIdentifier, final Map<String, Aggregate> aggs) {
    final SumAggregate aggregate =
        aggs.get(createAggregationName(customIdentifier, SUM_AGGREGATION)).sum();
    return getValue(aggregate.value(), aggregate.meta());
  }

  @Override
  public Pair<String, Aggregation> createAggregation(
      final String customIdentifier, final Script script, final String... fields) {
    final SumAggregation.Builder builder = new SumAggregation.Builder().script(script);
    AggregationResultMappingUtil.firstField(fields).ifPresent(builder::field);
    return Pair.of(
        createAggregationName(customIdentifier, SUM_AGGREGATION), builder.build()._toAggregation());
  }
}
