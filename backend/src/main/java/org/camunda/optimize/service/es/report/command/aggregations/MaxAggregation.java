/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.aggregations;

import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.metrics.Max;
import org.elasticsearch.search.aggregations.metrics.MaxAggregationBuilder;
import org.elasticsearch.search.aggregations.support.ValuesSourceAggregationBuilder;

import static org.camunda.optimize.service.es.report.command.util.ElasticsearchAggregationResultMappingUtil.mapToDoubleOrNull;
import static org.elasticsearch.search.aggregations.AggregationBuilders.max;

public class MaxAggregation extends AggregationStrategy<MaxAggregationBuilder> {

  private static final String MAX_AGGREGATION = "maxAggregation";

  @Override
  public Double getValueForAggregation(final String customIdentifier, final Aggregations aggs) {
    final Max aggregation = aggs.get(createAggregationName(customIdentifier, MAX_AGGREGATION));
    return mapToDoubleOrNull(aggregation.getValue());
  }

  @Override
  public ValuesSourceAggregationBuilder<MaxAggregationBuilder> createAggregationBuilderForAggregation(final String customIdentifier) {
    return max(createAggregationName(customIdentifier, MAX_AGGREGATION));
  }

  @Override
  public AggregationDto getAggregationType() {
    return new AggregationDto(AggregationType.MAX);
  }

}
