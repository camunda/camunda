/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.modules.view.process;

import com.google.common.collect.ImmutableMap;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.es.report.command.aggregations.AggregationStrategy;
import io.camunda.optimize.service.db.es.report.command.aggregations.AvgAggregation;
import io.camunda.optimize.service.db.es.report.command.aggregations.MaxAggregation;
import io.camunda.optimize.service.db.es.report.command.aggregations.MinAggregation;
import io.camunda.optimize.service.db.es.report.command.aggregations.PercentileAggregation;
import io.camunda.optimize.service.db.es.report.command.aggregations.SumAggregation;
import io.camunda.optimize.service.db.es.report.command.exec.ExecutionContext;
import io.camunda.optimize.service.db.es.report.command.modules.result.CompositeCommandResult.ViewMeasure;
import io.camunda.optimize.service.db.es.report.command.modules.result.CompositeCommandResult.ViewResult;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class ProcessViewMultiAggregation extends ProcessViewPart {

  private static final Map<AggregationType, AggregationStrategy<?>> AGGREGATION_STRATEGIES =
      ImmutableMap.<AggregationType, AggregationStrategy<?>>builder()
          .put(AggregationType.MIN, new MinAggregation())
          .put(AggregationType.MAX, new MaxAggregation())
          .put(AggregationType.AVERAGE, new AvgAggregation())
          .put(AggregationType.SUM, new SumAggregation())
          .put(AggregationType.PERCENTILE, new PercentileAggregation())
          .build();

  @Override
  public ViewResult createEmptyResult(final ExecutionContext<ProcessReportDataDto> context) {
    final ViewResult.ViewResultBuilder viewResultBuilder = ViewResult.builder();
    getAggregationStrategies(context.getReportData())
        .forEach(
            aggregationStrategy ->
                viewResultBuilder.viewMeasure(
                    ViewMeasure.builder()
                        .aggregationType(aggregationStrategy.getAggregationType())
                        .value(null)
                        .build()));
    return viewResultBuilder.build();
  }

  public List<AggregationStrategy<?>> getAggregationStrategies(
      final ProcessReportDataDto definitionData) {
    return definitionData.getConfiguration().getAggregationTypes().stream()
        .map(
            aggregationTypeDto -> {
              final AggregationStrategy<?> aggregationStrategy =
                  AGGREGATION_STRATEGIES.get(aggregationTypeDto.getType());
              if (aggregationStrategy instanceof PercentileAggregation) {
                return new PercentileAggregation(aggregationTypeDto.getValue());
              }
              return aggregationStrategy;
            })
        .collect(Collectors.toList());
  }
}
