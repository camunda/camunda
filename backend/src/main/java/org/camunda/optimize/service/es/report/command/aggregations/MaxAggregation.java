/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.aggregations;

import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.metrics.Max;
import org.elasticsearch.search.aggregations.support.ValuesSourceAggregationBuilder;

import static org.camunda.optimize.service.es.report.command.util.ElasticsearchAggregationResultMappingUtil.mapToDoubleOrNull;
import static org.elasticsearch.search.aggregations.AggregationBuilders.max;

public class MaxAggregation implements AggregationStrategy {
  private static final String MAX_AGGREGATION = "maxAggregation";

  @Override
  public Double getValue(final Aggregations aggs) {
    final Max aggregation = aggs.get(MAX_AGGREGATION);
    return mapToDoubleOrNull(aggregation.getValue());
  }

  @Override
  public ValuesSourceAggregationBuilder<?> getAggregationBuilder() {
    return max(MAX_AGGREGATION);
  }

  @Override
  public AggregationType getAggregationType() {
    return AggregationType.MAX;
  }
}
