/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.view.process.duration;

import static io.camunda.optimize.service.db.es.report.interpreter.util.DurationScriptUtilES.getDurationScript;
import static io.camunda.optimize.service.db.es.report.interpreter.util.DurationScriptUtilES.getUserTaskDurationScript;

import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import co.elastic.clients.util.Pair;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.es.report.interpreter.view.process.AbstractProcessViewMultiAggregationInterpreterES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.ViewMeasure;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.ViewResult;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.math3.util.Precision;

public abstract class AbstractProcessViewDurationInterpreterES
    extends AbstractProcessViewMultiAggregationInterpreterES {

  @Override
  public Map<String, Aggregation.Builder.ContainerBuilder> createAggregations(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return getAggregationStrategies(context.getReportData()).stream()
        .map(
            strategy ->
                strategy.createAggregationBuilder(
                    getScriptedAggregationField(context.getReportData())))
        .collect(Collectors.toMap(Pair::key, Pair::value));
  }

  @Override
  public ViewResult retrieveResult(
      final ResponseBody<?> response,
      final Map<String, Aggregate> aggs,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
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
