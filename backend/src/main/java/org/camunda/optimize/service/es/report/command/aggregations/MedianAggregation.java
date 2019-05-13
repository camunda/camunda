/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.aggregations;

import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.metrics.percentiles.tdigest.ParsedTDigestPercentiles;

import static org.camunda.optimize.service.es.report.command.util.ElasticsearchAggregationResultMappingUtil.mapToLong;
import static org.elasticsearch.search.aggregations.AggregationBuilders.percentiles;

public class MedianAggregation implements AggregationStrategy {
  private static final String MEDIAN_DURATION_AGGREGATION = "medianAggregatedDuration";

  @Override
  public long getValue(final Aggregations aggregations) {
    final ParsedTDigestPercentiles percentiles = aggregations.get(MEDIAN_DURATION_AGGREGATION);
    return mapToLong(percentiles);
  }

  @Override
  public AggregationBuilder getAggregationBuilder(final String fieldName) {
    return percentiles(MEDIAN_DURATION_AGGREGATION)
      .percentiles(50)
      .field(fieldName);
  }

  @Override
  public AggregationType getAggregationType() {
    return AggregationType.MEDIAN;
  }
}
