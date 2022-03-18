/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.aggregations;

import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.metrics.Avg;
import org.elasticsearch.search.aggregations.metrics.AvgAggregationBuilder;
import org.elasticsearch.search.aggregations.support.ValuesSourceAggregationBuilder;

import static org.camunda.optimize.service.es.report.command.util.ElasticsearchAggregationResultMappingUtil.mapToDoubleOrNull;
import static org.elasticsearch.search.aggregations.AggregationBuilders.avg;

public class AvgAggregation extends AggregationStrategy<AvgAggregationBuilder> {

  private static final String AVG_AGGREGATION = "avgAggregation";

  @Override
  public Double getValueForAggregation(final String customIdentifier, final Aggregations aggs) {
    final Avg aggregation = aggs.get(createAggregationName(customIdentifier, AVG_AGGREGATION));
    return mapToDoubleOrNull(aggregation.getValue());
  }

  @Override
  public ValuesSourceAggregationBuilder<AvgAggregationBuilder> createAggregationBuilderForAggregation(final String customIdentifier) {
    return avg(createAggregationName(customIdentifier, AVG_AGGREGATION));
  }

  @Override
  public AggregationDto getAggregationType() {
    return new AggregationDto(AggregationType.AVERAGE);
  }

}