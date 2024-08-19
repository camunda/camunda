/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.aggregations;

import static org.elasticsearch.search.aggregations.AggregationBuilders.percentiles;

import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import io.camunda.optimize.service.db.es.report.command.util.ElasticsearchAggregationResultMappingUtil;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.metrics.ParsedTDigestPercentiles;
import org.elasticsearch.search.aggregations.metrics.PercentilesAggregationBuilder;
import org.elasticsearch.search.aggregations.support.ValuesSourceAggregationBuilder;

public class PercentileAggregation extends AggregationStrategy<PercentilesAggregationBuilder> {

  private static final String PERCENTILE_AGGREGATION = "percentileAggregation";

  private Double percentileValue;

  public PercentileAggregation(final Double percentileValue) {
    this.percentileValue = percentileValue;
  }

  public PercentileAggregation() {}

  @Override
  public ValuesSourceAggregationBuilder<PercentilesAggregationBuilder>
      createAggregationBuilderForAggregation(final String customIdentifier) {
    return percentiles(
            createAggregationName(
                customIdentifier, String.valueOf(percentileValue), PERCENTILE_AGGREGATION))
        .percentiles(percentileValue);
  }

  @Override
  public Double getValueForAggregation(final String customIdentifier, final Aggregations aggs) {
    final ParsedTDigestPercentiles percentiles =
        aggs.get(
            createAggregationName(
                customIdentifier, String.valueOf(percentileValue), PERCENTILE_AGGREGATION));
    return ElasticsearchAggregationResultMappingUtil.mapToDoubleOrNull(
        percentiles, percentileValue);
  }

  @Override
  public AggregationDto getAggregationType() {
    return new AggregationDto(AggregationType.PERCENTILE, percentileValue);
  }
}
