/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.aggregations;

import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.service.es.report.command.util.ElasticsearchAggregationResultMappingUtil;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.metrics.ParsedTDigestPercentiles;
import org.elasticsearch.search.aggregations.support.ValuesSourceAggregationBuilder;

import static org.elasticsearch.search.aggregations.AggregationBuilders.percentiles;


public class MedianAggregation implements AggregationStrategy {
  private static final String MEDIAN_AGGREGATION = "medianAggregation";

  @Override
  public Double getValue(final Aggregations aggregations) {
    final ParsedTDigestPercentiles percentiles = aggregations.get(MEDIAN_AGGREGATION);
    return ElasticsearchAggregationResultMappingUtil.mapToDoubleOrNull(percentiles);
  }

  @Override
  public ValuesSourceAggregationBuilder<?> getAggregationBuilder() {
    return percentiles(MEDIAN_AGGREGATION)
      .percentiles(50);
  }

  @Override
  public AggregationType getAggregationType() {
    return AggregationType.MEDIAN;
  }
}
