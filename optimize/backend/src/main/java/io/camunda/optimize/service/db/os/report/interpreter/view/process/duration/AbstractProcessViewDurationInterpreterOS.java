/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.view.process.duration;

import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.os.report.aggregations.AggregationStrategyOS;
import io.camunda.optimize.service.db.os.report.interpreter.RawResult;
import io.camunda.optimize.service.db.os.report.interpreter.util.DurationScriptUtilOS;
import io.camunda.optimize.service.db.os.report.interpreter.view.process.AbstractProcessViewMultiAggregationInterpreterOS;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.view.process.duration.ProcessViewDurationInterpreterHelper;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.ViewResult;
import io.camunda.optimize.service.security.util.LocalDateUtil;
import io.camunda.optimize.util.types.MapUtil;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch.core.SearchResponse;

public abstract class AbstractProcessViewDurationInterpreterOS
    extends AbstractProcessViewMultiAggregationInterpreterOS {

  @Override
  public Map<String, Aggregation> createAggregations(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return getAggregationStrategies(context.getReportData()).stream()
        .map(
            strategy ->
                strategy.createAggregation(getScriptedAggregationField(context.getReportData())))
        .collect(MapUtil.pairCollector());
  }

  @Override
  public ViewResult retrieveResult(
      final SearchResponse<RawResult> response,
      final Map<String, Aggregate> aggs,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final Function<AggregationStrategyOS, Double> measureExtractor =
        aggregationStrategy -> aggregationStrategy.getValue(aggs);
    final List<AggregationStrategyOS> aggregationStrategies =
        getAggregationStrategies(context.getReportData());
    return ProcessViewDurationInterpreterHelper.retrieveResult(
        aggregationStrategies, measureExtractor);
  }

  protected abstract String getReferenceDateFieldName(final ProcessReportDataDto reportData);

  protected abstract String getDurationFieldName(final ProcessReportDataDto reportData);

  private Script getScriptedAggregationField(final ProcessReportDataDto reportData) {
    return reportData.isUserTaskReport()
        ? DurationScriptUtilOS.getUserTaskDurationScript(
            LocalDateUtil.getCurrentDateTime().toInstant().toEpochMilli(),
            getDurationFieldName(reportData))
        : DurationScriptUtilOS.getDurationScript(
            LocalDateUtil.getCurrentDateTime().toInstant().toEpochMilli(),
            getDurationFieldName(reportData),
            getReferenceDateFieldName(reportData));
  }
}
