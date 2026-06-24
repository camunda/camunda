/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.interpreter.view.process.duration;

import io.camunda.optimize.dto.optimize.query.report.single.configuration.process_part.ProcessPartDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.aggregations.AggregationStrategy;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.ViewMeasure;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.ViewResult;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.util.List;
import java.util.function.Function;
import org.apache.commons.math3.util.Precision;

public final class ProcessViewDurationInterpreterHelper {

  private ProcessViewDurationInterpreterHelper() {}

  public static ProcessPartDto getProcessPart(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return context
        .getReportConfiguration()
        .getProcessPart()
        .orElseThrow(() -> new OptimizeRuntimeException("Missing ProcessPart"));
  }

  public static <AGGREGATION_STRATEGY extends AggregationStrategy> ViewResult retrieveResult(
      final List<AGGREGATION_STRATEGY> aggregationStrategies,
      final Function<AGGREGATION_STRATEGY, Double> measureExtractor) {
    final ViewResult.ViewResultBuilder viewResultBuilder = ViewResult.builder();
    aggregationStrategies.forEach(
        aggregationStrategy -> {
          Double measureResult = measureExtractor.apply(aggregationStrategy);
          if (measureResult != null) {
            // rounding to closest integer since the lowest precision
            // for duration in the data is milliseconds anyway for data types.
            measureResult = Precision.round(measureResult, 0);
          }
          viewResultBuilder.viewMeasure(
              ViewMeasure.builder()
                  .aggregationType(aggregationStrategy.getAggregationType())
                  .value(measureResult)
                  .build());
        });
    return viewResultBuilder.build();
  }
}
