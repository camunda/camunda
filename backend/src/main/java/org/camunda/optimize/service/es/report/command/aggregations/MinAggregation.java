/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.aggregations;

import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.metrics.Min;
import org.elasticsearch.search.aggregations.metrics.MinAggregationBuilder;
import org.elasticsearch.search.aggregations.support.ValuesSourceAggregationBuilder;

import static org.camunda.optimize.service.es.report.command.util.ElasticsearchAggregationResultMappingUtil.mapToDoubleOrNull;
import static org.elasticsearch.search.aggregations.AggregationBuilders.min;

public class MinAggregation extends AggregationStrategy<MinAggregationBuilder> {

  private static final String MIN_AGGREGATION = "minAggregation";

  @Override
  public Double getValueForAggregation(final String customIdentifier, final Aggregations aggs) {
    final Min aggregation = aggs.get(createAggregationName(customIdentifier, MIN_AGGREGATION));
    return mapToDoubleOrNull(aggregation.getValue());
  }

  @Override
  public ValuesSourceAggregationBuilder<MinAggregationBuilder> createAggregationBuilderForAggregation(final String customIdentifier) {
    return min(createAggregationName(customIdentifier, MIN_AGGREGATION));
  }

  @Override
  public AggregationDto getAggregationType() {
    return new AggregationDto(AggregationType.MIN);
  }

}
