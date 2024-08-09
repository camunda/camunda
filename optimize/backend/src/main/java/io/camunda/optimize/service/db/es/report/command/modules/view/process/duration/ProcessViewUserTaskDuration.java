/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.modules.view.process.duration;

import static io.camunda.optimize.service.db.es.report.command.util.DurationScriptUtil.getUserTaskDurationScript;
import static io.camunda.optimize.service.db.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;

import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import io.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import io.camunda.optimize.service.db.es.report.command.exec.ExecutionContext;
import io.camunda.optimize.service.db.es.report.command.modules.result.CompositeCommandResult.ViewMeasure;
import io.camunda.optimize.service.db.es.report.command.modules.result.CompositeCommandResult.ViewResult;
import io.camunda.optimize.service.db.es.report.command.modules.result.CompositeCommandResult.ViewResult.ViewResultBuilder;
import io.camunda.optimize.service.db.es.report.command.modules.view.process.ProcessViewMultiAggregation;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.math3.util.Precision;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Primary
public class ProcessViewUserTaskDuration extends ProcessViewMultiAggregation {

  @Override
  public ViewProperty getViewProperty(final ExecutionContext<ProcessReportDataDto> context) {
    return ViewProperty.DURATION;
  }

  @Override
  public List<AggregationBuilder> createAggregations(
      final ExecutionContext<ProcessReportDataDto> context) {
    return context.getReportData().getConfiguration().getUserTaskDurationTimes().stream()
        .flatMap(
            userTaskDurationTime ->
                getAggregationStrategies(context.getReportData()).stream()
                    .map(
                        strategy ->
                            strategy
                                .createAggregationBuilder(userTaskDurationTime.getId())
                                .script(getScriptedAggregationField(userTaskDurationTime))))
        .collect(Collectors.toList());
  }

  @Override
  public ViewResult retrieveResult(
      final SearchResponse response,
      final Aggregations aggs,
      final ExecutionContext<ProcessReportDataDto> context) {
    final ViewResultBuilder viewResultBuilder = ViewResult.builder();
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
                              ViewMeasure.builder()
                                  .aggregationType(aggregationStrategy.getAggregationType())
                                  .userTaskDurationTime(userTaskDurationTime)
                                  .value(measureResult)
                                  .build());
                        }));
    return viewResultBuilder.build();
  }

  @Override
  public ViewResult createEmptyResult(final ExecutionContext<ProcessReportDataDto> context) {
    final ViewResult.ViewResultBuilder viewResultBuilder = ViewResult.builder();
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
                                ViewMeasure.builder()
                                    .aggregationType(aggregationStrategy.getAggregationType())
                                    .userTaskDurationTime(userTaskDurationTime)
                                    .value(null)
                                    .build())));
    return viewResultBuilder.build();
  }

  @Override
  public void addViewAdjustmentsForCommandKeyGeneration(
      final ProcessReportDataDto dataForCommandKey) {
    ProcessViewDto view = new ProcessViewDto();
    view.setEntity(ProcessViewEntity.USER_TASK);
    view.setProperties(ViewProperty.DURATION);
    dataForCommandKey.setView(view);
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
