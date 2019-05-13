/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.aggregations;

import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.metrics.max.Max;

import static org.camunda.optimize.service.es.report.command.util.ElasticsearchAggregationResultMappingUtil.mapToLong;
import static org.elasticsearch.search.aggregations.AggregationBuilders.max;

public class MaxAggregation implements AggregationStrategy {
  private static final String MAX_DURATION_AGGREGATION = "maxAggregatedDuration";

  @Override
  public long getValue(final Aggregations aggs) {
    final Max aggregation = aggs.get(MAX_DURATION_AGGREGATION);
    return mapToLong(aggregation.getValue());
  }

  @Override
  public AggregationBuilder getAggregationBuilder(final String fieldName) {
    return max(MAX_DURATION_AGGREGATION).field(fieldName);
  }

  @Override
  public AggregationType getAggregationType() {
    return AggregationType.MAX;
  }
}
