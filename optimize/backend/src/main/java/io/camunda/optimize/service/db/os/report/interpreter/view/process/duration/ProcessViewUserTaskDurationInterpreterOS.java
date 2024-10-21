/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.view.process.duration;

import static io.camunda.optimize.service.db.report.plan.process.ProcessView.PROCESS_VIEW_USER_TASK_DURATION;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;

import io.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.os.report.aggregations.AggregationStrategyOS;
import io.camunda.optimize.service.db.os.report.interpreter.RawResult;
import io.camunda.optimize.service.db.os.report.interpreter.util.DurationScriptUtilOS;
import io.camunda.optimize.service.db.os.report.interpreter.view.process.AbstractProcessViewMultiAggregationInterpreterOS;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessView;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.service.util.configuration.condition.OpenSearchCondition;
import io.camunda.optimize.util.types.MapUtil;
import java.util.Map;
import java.util.Set;
import org.apache.commons.math3.util.Precision;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch.core.SearchResponse;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@Conditional(OpenSearchCondition.class)
public class ProcessViewUserTaskDurationInterpreterOS
    extends AbstractProcessViewMultiAggregationInterpreterOS {

  @Override
  public Set<ProcessView> getSupportedViews() {
    return Set.of(PROCESS_VIEW_USER_TASK_DURATION);
  }

  @Override
  public Map<String, Aggregation> createAggregations(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return context.getReportData().getConfiguration().getUserTaskDurationTimes().stream()
        .flatMap(
            userTaskDurationTime ->
                getAggregationStrategies(context.getReportData()).stream()
                    .map(
                        strategy ->
                            strategy.createAggregation(
                                userTaskDurationTime.getId(),
                                getScriptedAggregationField(userTaskDurationTime))))
        .collect(MapUtil.pairCollector());
  }

  @Override
  public CompositeCommandResult.ViewResult retrieveResult(
      final SearchResponse<RawResult> response,
      final Map<String, Aggregate> aggs,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final CompositeCommandResult.ViewResult.ViewResultBuilder viewResultBuilder =
        CompositeCommandResult.ViewResult.builder();
    context
        .getReportData()
        .getConfiguration()
        .getUserTaskDurationTimes()
        .forEach(
            userTaskDurationTime ->
                getAggregationStrategies(context.getReportData())
                    .forEach(
                        aggregationStrategy -> {
                          final Double measureResult =
                              getMeasureResult(aggregationStrategy, userTaskDurationTime, aggs);
                          viewResultBuilder.viewMeasure(
                              buildViewMeasure(
                                  aggregationStrategy, userTaskDurationTime, measureResult));
                        }));
    return viewResultBuilder.build();
  }

  @Override
  public CompositeCommandResult.ViewResult createEmptyResult(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final CompositeCommandResult.ViewResult.ViewResultBuilder viewResultBuilder =
        CompositeCommandResult.ViewResult.builder();
    context
        .getReportData()
        .getConfiguration()
        .getUserTaskDurationTimes()
        .forEach(
            userTaskDurationTime ->
                getAggregationStrategies(context.getReportData())
                    .forEach(
                        aggregationStrategy ->
                            viewResultBuilder.viewMeasure(
                                buildViewMeasure(
                                    aggregationStrategy, userTaskDurationTime, null))));
    return viewResultBuilder.build();
  }

  private Double getMeasureResult(
      final AggregationStrategyOS aggregationStrategy,
      final UserTaskDurationTime userTaskDurationTime,
      final Map<String, Aggregate> aggs) {
    Double measureResult = aggregationStrategy.getValue(userTaskDurationTime.getId(), aggs);
    if (measureResult != null) {
      // rounding to closest integer since the lowest precision
      // for duration in the data is milliseconds anyway for data types.
      measureResult = Precision.round(measureResult, 0);
    }
    return measureResult;
  }

  private CompositeCommandResult.ViewMeasure buildViewMeasure(
      final AggregationStrategyOS aggregationStrategy,
      final UserTaskDurationTime userTaskDurationTime,
      final Double measureResult) {
    return CompositeCommandResult.ViewMeasure.builder()
        .aggregationType(aggregationStrategy.getAggregationType())
        .userTaskDurationTime(userTaskDurationTime)
        .value(measureResult)
        .build();
  }

  private Script getScriptedAggregationField(final UserTaskDurationTime userTaskDurationTime) {
    return DurationScriptUtilOS.getUserTaskDurationScript(
        LocalDateUtil.getCurrentDateTime().toInstant().toEpochMilli(),
        getDurationFieldName(userTaskDurationTime));
  }

  private String getDurationFieldName(final UserTaskDurationTime userTaskDurationTime) {
    return FLOW_NODE_INSTANCES + "." + userTaskDurationTime.getDurationFieldName();
  }
}
