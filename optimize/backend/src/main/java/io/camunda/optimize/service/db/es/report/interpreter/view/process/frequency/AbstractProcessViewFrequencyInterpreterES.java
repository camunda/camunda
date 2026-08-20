/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.view.process.frequency;

import static io.camunda.optimize.service.db.DatabaseConstants.FREQUENCY_AGGREGATION;

import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.aggregations.FilterAggregate;
import co.elastic.clients.elasticsearch.core.search.ResponseBody;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.es.report.interpreter.view.process.ProcessViewInterpreterES;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.view.process.frequency.ProcessViewFrequencyInterpreter;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult;
import java.util.Map;

public abstract class AbstractProcessViewFrequencyInterpreterES
    implements ProcessViewInterpreterES, ProcessViewFrequencyInterpreter {

  @Override
  public CompositeCommandResult.ViewResult createEmptyResult(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return createViewResult(null);
  }

  @Override
  public Map<String, Aggregation.Builder.ContainerBuilder> createAggregations(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final Aggregation.Builder builder = new Aggregation.Builder();
    return Map.of(FREQUENCY_AGGREGATION, builder.filter(f -> f.matchAll(m -> m)));
  }

  @Override
  public CompositeCommandResult.ViewResult retrieveResult(
      final ResponseBody<?> response,
      final Map<String, Aggregate> aggs,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final FilterAggregate count = aggs.get(FREQUENCY_AGGREGATION).filter();
    return createViewResult((double) count.docCount());
  }
}
