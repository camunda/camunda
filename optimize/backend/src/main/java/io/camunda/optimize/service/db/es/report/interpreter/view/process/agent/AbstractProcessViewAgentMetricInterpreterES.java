/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.view.process.agent;

import co.elastic.clients.elasticsearch._types.Script;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import co.elastic.clients.util.Pair;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.es.report.aggregations.AggregationStrategyES;
import io.camunda.optimize.service.db.es.report.interpreter.view.process.AbstractProcessViewMultiAggregationInterpreterES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.ViewMeasure;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.ViewResult;
import java.util.Map;
import java.util.stream.Collectors;

public abstract class AbstractProcessViewAgentMetricInterpreterES
    extends AbstractProcessViewMultiAggregationInterpreterES {

  @Override
  public Map<String, Aggregation.Builder.ContainerBuilder> createAggregations(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final String aggregationFieldName = getAggregationFieldName();
    final Script aggregationScript = getAggregationScript();

    return getAggregationStrategies(context.getReportData()).stream()
        .map(
            strategy ->
                aggregationFieldName == null
                    ? strategy.createAggregationBuilder(aggregationScript)
                    : strategy.createAggregationBuilder(aggregationScript, aggregationFieldName))
        .collect(Collectors.toMap(Pair::key, Pair::value));
  }

  @Override
  public ViewResult retrieveResult(
      final ResponseBody<?> response,
      final Map<String, Aggregate> aggs,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final ViewResult.ViewResultBuilder viewResultBuilder = ViewResult.builder();
    for (final AggregationStrategyES<?> aggregationStrategy :
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
