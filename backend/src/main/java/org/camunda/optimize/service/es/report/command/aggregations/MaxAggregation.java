/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.aggregations;

import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.metrics.max.Max;
import org.elasticsearch.search.aggregations.support.ValuesSourceAggregationBuilder;

import static org.camunda.optimize.service.es.report.command.util.ElasticsearchAggregationResultMappingUtil.mapToLongOrNull;
import static org.elasticsearch.search.aggregations.AggregationBuilders.max;

public class MaxAggregation implements AggregationStrategy {
  private static final String MAX_DURATION_AGGREGATION = "maxAggregatedDuration";

  @Override
  public Long getValue(final Aggregations aggs) {
    final Max aggregation = aggs.get(MAX_DURATION_AGGREGATION);
    return mapToLongOrNull(aggregation.getValue());
  }

  @Override
  public ValuesSourceAggregationBuilder getAggregationBuilder() {
    return max(MAX_DURATION_AGGREGATION);
  }

  @Override
  public AggregationType getAggregationType() {
    return AggregationType.MAX;
  }
}
