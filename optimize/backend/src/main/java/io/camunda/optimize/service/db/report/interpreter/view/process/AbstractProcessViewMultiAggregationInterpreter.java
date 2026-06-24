/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.interpreter.view.process;

import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.aggregations.AggregationStrategy;
import io.camunda.optimize.service.db.report.aggregations.HasAggregationStrategies;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.ViewMeasure;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.ViewResult;
import java.util.List;
import java.util.stream.Collectors;

public abstract class AbstractProcessViewMultiAggregationInterpreter<
        AGGREGATION_STRATEGY extends AggregationStrategy>
    implements HasAggregationStrategies<AGGREGATION_STRATEGY> {
  protected abstract AGGREGATION_STRATEGY getAggregationStrategy(
      final AggregationDto aggregationDto);

  public ViewResult createEmptyResult(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
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

  @Override
  public List<AGGREGATION_STRATEGY> getAggregationStrategies(
      final ProcessReportDataDto definitionData) {
    return definitionData.getConfiguration().getAggregationTypes().stream()
        .map(this::getAggregationStrategy)
        .collect(Collectors.toList());
  }
}
