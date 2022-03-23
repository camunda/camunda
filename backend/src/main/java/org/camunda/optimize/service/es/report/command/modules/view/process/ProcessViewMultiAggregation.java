/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.modules.view.process;

import com.google.common.collect.ImmutableMap;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.service.es.report.command.aggregations.AggregationStrategy;
import org.camunda.optimize.service.es.report.command.aggregations.AvgAggregation;
import org.camunda.optimize.service.es.report.command.aggregations.MaxAggregation;
import org.camunda.optimize.service.es.report.command.aggregations.MinAggregation;
import org.camunda.optimize.service.es.report.command.aggregations.PercentileAggregation;
import org.camunda.optimize.service.es.report.command.aggregations.SumAggregation;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.ViewMeasure;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.ViewResult;

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
      .forEach(aggregationStrategy -> viewResultBuilder.viewMeasure(
        ViewMeasure.builder().aggregationType(aggregationStrategy.getAggregationType()).value(null).build()
      ));
    return viewResultBuilder.build();
  }

  public List<AggregationStrategy<?>> getAggregationStrategies(final ProcessReportDataDto definitionData) {
    return definitionData.getConfiguration().getAggregationTypes().stream()
      .map(aggregationTypeDto -> {
        final AggregationStrategy<?> aggregationStrategy = AGGREGATION_STRATEGIES.get(aggregationTypeDto.getType());
        if (aggregationStrategy instanceof PercentileAggregation) {
          return new PercentileAggregation(aggregationTypeDto.getValue());
        }
        return aggregationStrategy;
      })
      .collect(Collectors.toList());
  }

}
