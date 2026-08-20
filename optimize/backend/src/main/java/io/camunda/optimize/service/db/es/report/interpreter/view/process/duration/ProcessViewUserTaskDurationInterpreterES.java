/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.view.process.duration;

import static io.camunda.optimize.service.db.es.report.interpreter.util.DurationScriptUtilES.getUserTaskDurationScript;
import static io.camunda.optimize.service.db.report.plan.process.ProcessView.PROCESS_VIEW_USER_TASK_DURATION;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;

import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import co.elastic.clients.util.Pair;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.es.report.interpreter.view.process.AbstractProcessViewMultiAggregationInterpreterES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessView;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.service.util.configuration.condition.ElasticSearchCondition;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.apache.commons.math3.util.Precision;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Component
@Primary
@Conditional(ElasticSearchCondition.class)
public class ProcessViewUserTaskDurationInterpreterES
    extends AbstractProcessViewMultiAggregationInterpreterES {

  @Override
  public Set<ProcessView> getSupportedViews() {
    return Set.of(PROCESS_VIEW_USER_TASK_DURATION);
  }

  @Override
  public Map<String, Aggregation.Builder.ContainerBuilder> createAggregations(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return context.getReportData().getConfiguration().getUserTaskDurationTimes().stream()
        .flatMap(
            userTaskDurationTime ->
                getAggregationStrategies(context.getReportData()).stream()
                    .map(
                        strategy ->
                            strategy.createAggregationBuilder(
                                userTaskDurationTime.getId(),
                                getScriptedAggregationField(userTaskDurationTime))))
        .collect(Collectors.toMap(Pair::key, Pair::value));
  }

  @Override
  public CompositeCommandResult.ViewResult retrieveResult(
      final ResponseBody<?> response,
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
                          Double measureResult =
                              aggregationStrategy.getValue(userTaskDurationTime.getId(), aggs);
                          if (measureResult != null) {
                            // rounding to closest integer since the lowest precision
                            // for duration in the data is milliseconds anyway for data types.
                            measureResult = Precision.round(measureResult, 0);
                          }
                          viewResultBuilder.viewMeasure(
                              CompositeCommandResult.ViewMeasure.builder()
                                  .aggregationType(aggregationStrategy.getAggregationType())
                                  .userTaskDurationTime(userTaskDurationTime)
                                  .value(measureResult)
                                  .build());
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
                                CompositeCommandResult.ViewMeasure.builder()
                                    .aggregationType(aggregationStrategy.getAggregationType())
                                    .userTaskDurationTime(userTaskDurationTime)
                                    .value(null)
                                    .build())));
    return viewResultBuilder.build();
  }

  private Script getScriptedAggregationField(final UserTaskDurationTime userTaskDurationTime) {
    return getUserTaskDurationScript(
        LocalDateUtil.getCurrentDateTime().toInstant().toEpochMilli(),
        getDurationFieldName(userTaskDurationTime));
  }

  private String getDurationFieldName(final UserTaskDurationTime userTaskDurationTime) {
    return FLOW_NODE_INSTANCES + "." + userTaskDurationTime.getDurationFieldName();
  }
}
