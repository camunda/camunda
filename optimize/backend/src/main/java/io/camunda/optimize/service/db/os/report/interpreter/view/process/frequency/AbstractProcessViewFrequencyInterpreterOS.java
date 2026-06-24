/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.os.report.interpreter.view.process.frequency;

import static io.camunda.optimize.service.db.DatabaseConstants.FREQUENCY_AGGREGATION;
import static io.camunda.optimize.service.db.os.client.dsl.AggregationDSL.filterAggregation;
import static io.camunda.optimize.service.db.os.client.dsl.QueryDSL.matchAll;

import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.os.report.interpreter.RawResult;
import io.camunda.optimize.service.db.os.report.interpreter.view.process.ProcessViewInterpreterOS;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.view.process.frequency.ProcessViewFrequencyInterpreter;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import io.camunda.optimize.service.db.report.result.CompositeCommandResult.ViewResult;
import java.util.Map;
import org.opensearch.client.opensearch._types.aggregations.Aggregate;
import org.opensearch.client.opensearch._types.aggregations.Aggregation;
import org.opensearch.client.opensearch._types.aggregations.FilterAggregate;
import org.opensearch.client.opensearch.core.SearchResponse;

public abstract class AbstractProcessViewFrequencyInterpreterOS
    implements ProcessViewInterpreterOS, ProcessViewFrequencyInterpreter {

  @Override
  public ViewResult createEmptyResult(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return createViewResult(null);
  }

  @Override
  public Map<String, Aggregation> createAggregations(
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    return Map.of(FREQUENCY_AGGREGATION, filterAggregation(matchAll()));
  }

  @Override
  public ViewResult retrieveResult(
      final SearchResponse<RawResult> response,
      final Map<String, Aggregate> aggregations,
      final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> context) {
    final FilterAggregate count = aggregations.get(FREQUENCY_AGGREGATION).filter();
    return createViewResult((double) count.docCount());
  }
}
