/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.aggregations;

import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.metrics.Sum;
import org.elasticsearch.search.aggregations.metrics.SumAggregationBuilder;
import org.elasticsearch.search.aggregations.support.ValuesSourceAggregationBuilder;

import static org.camunda.optimize.service.es.report.command.util.ElasticsearchAggregationResultMappingUtil.mapToDoubleOrNull;
import static org.elasticsearch.search.aggregations.AggregationBuilders.sum;

public class SumAggregation extends AggregationStrategy<SumAggregationBuilder> {

  private static final String SUM_AGGREGATION = "sumAggregation";

  @Override
  public Double getValueForAggregation(final String customIdentifier, final Aggregations aggs) {
    final Sum aggregation = aggs.get(createAggregationName(customIdentifier, SUM_AGGREGATION));
    return mapToDoubleOrNull(aggregation.getValue());
  }

  @Override
  public ValuesSourceAggregationBuilder<SumAggregationBuilder> createAggregationBuilderForAggregation(final String customIdentifier) {
    return sum(createAggregationName(customIdentifier, SUM_AGGREGATION));
  }

  @Override
  public AggregationDto getAggregationType() {
    return new AggregationDto(AggregationType.SUM);
  }

}
