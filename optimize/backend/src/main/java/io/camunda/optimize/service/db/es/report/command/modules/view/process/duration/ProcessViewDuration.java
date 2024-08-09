/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.modules.view.process.duration;

import static io.camunda.optimize.service.db.es.report.command.util.DurationScriptUtil.getDurationScript;
import static io.camunda.optimize.service.db.es.report.command.util.DurationScriptUtil.getUserTaskDurationScript;

import io.camunda.optimize.dto.optimize.query.report.single.ViewProperty;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.es.report.command.exec.ExecutionContext;
import io.camunda.optimize.service.db.es.report.command.modules.result.CompositeCommandResult.ViewMeasure;
import io.camunda.optimize.service.db.es.report.command.modules.result.CompositeCommandResult.ViewResult;
import io.camunda.optimize.service.db.es.report.command.modules.view.process.ProcessViewMultiAggregation;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.commons.math3.util.Precision;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.script.Script;
import org.elasticsearch.search.aggregations.AggregationBuilder;
import org.elasticsearch.search.aggregations.Aggregations;

public abstract class ProcessViewDuration extends ProcessViewMultiAggregation {

  @Override
  public ViewProperty getViewProperty(final ExecutionContext<ProcessReportDataDto> context) {
    return ViewProperty.DURATION;
  }

  @Override
  public List<AggregationBuilder> createAggregations(
      final ExecutionContext<ProcessReportDataDto> context) {
    return getAggregationStrategies(context.getReportData()).stream()
        .map(
            strategy ->
                strategy
                    .createAggregationBuilder()
                    .script(getScriptedAggregationField(context.getReportData())))
        .collect(Collectors.toList());
  }

  @Override
  public ViewResult retrieveResult(
      final SearchResponse response,
      final Aggregations aggs,
      final ExecutionContext<ProcessReportDataDto> context) {
    final ViewResult.ViewResultBuilder viewResultBuilder = ViewResult.builder();
    getAggregationStrategies(context.getReportData())
        .forEach(
            aggregationStrategy -> {
              Double measureResult = aggregationStrategy.getValue(aggs);
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

  protected abstract String getReferenceDateFieldName(final ProcessReportDataDto reportData);

  protected abstract String getDurationFieldName(final ProcessReportDataDto reportData);

  private Script getScriptedAggregationField(final ProcessReportDataDto reportData) {
    return reportData.isUserTaskReport()
        ? getUserTaskDurationScript(
            LocalDateUtil.getCurrentDateTime().toInstant().toEpochMilli(),
            getDurationFieldName(reportData))
        : getDurationScript(
            LocalDateUtil.getCurrentDateTime().toInstant().toEpochMilli(),
            getDurationFieldName(reportData),
            getReferenceDateFieldName(reportData));
  }
}
