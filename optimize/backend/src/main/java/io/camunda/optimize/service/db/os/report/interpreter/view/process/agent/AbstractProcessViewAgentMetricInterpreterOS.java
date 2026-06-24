/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.view.process.agent;

import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.os.report.aggregations.AggregationStrategyOS;
import io.camunda.optimize.service.db.os.report.interpreter.RawResult;
import io.camunda.optimize.service.db.os.report.interpreter.view.process.AbstractProcessViewMultiAggregationInterpreterOS;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.ViewMeasure;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.ViewResult;
import io.camunda.optimize.util.types.MapUtil;
import java.util.Map;
import org.opensearch.client.opensearch._types.Script;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch.core.SearchResponse;

public abstract class AbstractProcessViewAgentMetricInterpreterOS
    extends AbstractProcessViewMultiAggregationInterpreterOS {

  @Override
  public Map<String, Aggregation> createAggregations(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final String aggregationFieldName = getAggregationFieldName();
    final Script aggregationScript = getAggregationScript();

    // A subclass must supply a field name or a script for the metric.
    if (aggregationFieldName == null && aggregationScript == null) {
      throw new IllegalStateException(
          getClass().getSimpleName()
              + " must override getAggregationFieldName() or getAggregationScript()");
    }

    return getAggregationStrategies(context.getReportData()).stream()
        .map(
            strategy ->
                aggregationFieldName == null
                    ? strategy.createAggregation(aggregationScript)
                    : strategy.createAggregation(aggregationScript, aggregationFieldName))
        .collect(MapUtil.pairCollector());
  }

  @Override
  public ViewResult retrieveResult(
      final SearchResponse<RawResult> response,
      final Map<String, Aggregate> aggs,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final ViewResult.ViewResultBuilder viewResultBuilder = ViewResult.builder();
    for (final AggregationStrategyOS aggregationStrategy :
        getAggregationStrategies(context.getReportData())) {
      viewResultBuilder.viewMeasure(
          ViewMeasure.builder()
              .aggregationType(aggregationStrategy.getAggregationType())
              .value(aggregationStrategy.getValue(aggs))
              .build());
    }
    return viewResultBuilder.build();
  }

  protected String getAggregationFieldName() {
    return null;
  }

  protected Script getAggregationScript() {
    return null;
  }
}
