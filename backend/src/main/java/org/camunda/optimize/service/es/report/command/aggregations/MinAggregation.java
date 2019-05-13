/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.aggregations;

import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.metrics.min.Min;

import static org.camunda.optimize.service.es.report.command.util.ElasticsearchAggregationResultMappingUtil.mapToLong;
import static org.elasticsearch.search.aggregations.AggregationBuilders.min;

public class MinAggregation implements AggregationStrategy {
  private static final String MIN_DURATION_AGGREGATION = "minAggregatedDuration";

  @Override
  public long getValue(final Aggregations aggs) {
    final Min aggregation = aggs.get(MIN_DURATION_AGGREGATION);
    return mapToLong(aggregation.getValue());
  }

  @Override
  public AggregationBuilder getAggregationBuilder(final String fieldName) {
    return min(MIN_DURATION_AGGREGATION).field(fieldName);
  }

  @Override
  public AggregationType getAggregationType() {
    return AggregationType.MIN;
  }

}
