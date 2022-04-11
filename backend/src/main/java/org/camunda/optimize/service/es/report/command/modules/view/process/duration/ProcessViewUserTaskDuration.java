/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.modules.view.process.duration;

import org.apache.commons.math3.util.Precision;
import org.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.UserTaskDurationTime;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.view.ProcessViewEntity;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.ViewMeasure;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.ViewResult;
import org.camunda.optimize.service.es.report.command.modules.result.CompositeCommandResult.ViewResult.ViewResultBuilder;
import org.camunda.optimize.service.es.report.command.modules.view.process.ProcessViewMultiAggregation;
import org.camunda.optimize.service.security.util.LocalDateUtil;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.es.report.command.util.DurationScriptUtil.getUserTaskDurationScript;
import static org.camunda.optimize.service.es.schema.index.ProcessInstanceIndex.FLOW_NODE_INSTANCES;

@Component
@Scope(value = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
@Primary
public class ProcessViewUserTaskDuration extends ProcessViewMultiAggregation {

  @Override
  public ViewProperty getViewProperty(final ExecutionContext<ProcessReportDataDto> context) {
    return ViewProperty.DURATION;
  }

  @Override
  public List<AggregationBuilder> createAggregations(final ExecutionContext<ProcessReportDataDto> context) {
    return context.getReportData().getConfiguration().getUserTaskDurationTimes().stream()
      .flatMap(userTaskDurationTime -> getAggregationStrategies(context.getReportData()).stream()
        .map(strategy -> strategy.createAggregationBuilder(userTaskDurationTime.getId())
          .script(getScriptedAggregationField(userTaskDurationTime)))
      )
      .collect(Collectors.toList());
  }

  @Override
  public ViewResult retrieveResult(final SearchResponse response,
                                   final Aggregations aggs,
                                   final ExecutionContext<ProcessReportDataDto> context) {
    final ViewResultBuilder viewResultBuilder = ViewResult.builder();
    context.getReportData().getConfiguration().getUserTaskDurationTimes()
      .forEach(userTaskDurationTime -> getAggregationStrategies(context.getReportData())
        .forEach(aggregationStrategy -> {
          Double measureResult = aggregationStrategy.getValue(userTaskDurationTime.getId(), aggs);
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
              .build()
          );
        })
      );
    return viewResultBuilder.build();
  }

  @Override
  public ViewResult createEmptyResult(final ExecutionContext<ProcessReportDataDto> context) {
    final ViewResult.ViewResultBuilder viewResultBuilder = ViewResult.builder();
    context.getReportData().getConfiguration().getUserTaskDurationTimes()
      .forEach(userTaskDurationTime -> getAggregationStrategies(context.getReportData())
        .forEach(aggregationStrategy -> viewResultBuilder.viewMeasure(
          ViewMeasure.builder()
            .aggregationType(aggregationStrategy.getAggregationType())
            .userTaskDurationTime(userTaskDurationTime)
            .value(null).build()
        ))
      );
    return viewResultBuilder.build();
  }

  @Override
  public void addViewAdjustmentsForCommandKeyGeneration(final ProcessReportDataDto dataForCommandKey) {
    ProcessViewDto view = new ProcessViewDto();
    view.setEntity(ProcessViewEntity.USER_TASK);
    view.setProperties(ViewProperty.DURATION);
    dataForCommandKey.setView(view);
  }

  private Script getScriptedAggregationField(final UserTaskDurationTime userTaskDurationTime) {
    return getUserTaskDurationScript(
      LocalDateUtil.getCurrentDateTime().toInstant().toEpochMilli(),
      getDurationFieldName(userTaskDurationTime)
    );
  }

  private String getDurationFieldName(final UserTaskDurationTime userTaskDurationTime) {
    return FLOW_NODE_INSTANCES + "." + userTaskDurationTime.getDurationFieldName();
  }
}
