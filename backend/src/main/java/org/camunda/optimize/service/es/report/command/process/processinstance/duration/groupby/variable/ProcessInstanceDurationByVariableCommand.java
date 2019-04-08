/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.variable;

import com.google.common.collect.ImmutableList;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.duration.AggregationResultDto;
import org.camunda.optimize.service.es.schema.type.ProcessInstanceType;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.metrics.percentiles.tdigest.ParsedTDigestPercentiles;
import org.elasticsearch.search.aggregations.metrics.stats.ParsedStats;

import java.util.List;

import static org.camunda.optimize.service.es.report.command.util.ElasticsearchAggregationResultMappingUtil.mapToLong;
import static org.elasticsearch.search.aggregations.AggregationBuilders.percentiles;
import static org.elasticsearch.search.aggregations.AggregationBuilders.stats;

public class ProcessInstanceDurationByVariableCommand
  extends AbstractProcessInstanceDurationByVariableCommand {

  private static final String STATS_DURATION_AGGREGATION = "statsAggregatedDuration";
  private static final String MEDIAN_DURATION_AGGREGATION = "medianAggregatedDuration";

  @Override
  protected AggregationResultDto processAggregationOperation(Aggregations aggs) {
    ParsedStats statsAggregation = aggs.get(STATS_DURATION_AGGREGATION);
    ParsedTDigestPercentiles medianAggregation = aggs.get(MEDIAN_DURATION_AGGREGATION);

    return new AggregationResultDto(
      mapToLong(statsAggregation.getMin()),
      mapToLong(statsAggregation.getMax()),
      mapToLong(statsAggregation.getAvg()),
      mapToLong(medianAggregation)
    );
  }

  @Override
  protected List<AggregationBuilder> createOperationsAggregations() {
    return
      ImmutableList.<AggregationBuilder>builder()
        .add(
          stats(STATS_DURATION_AGGREGATION)
            .field(ProcessInstanceType.DURATION))
        .add(
          percentiles(MEDIAN_DURATION_AGGREGATION)
            .percentiles(50)
            .field(ProcessInstanceType.DURATION))
        .build();
  }
}
