/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.interpreter.view.process.agent;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.AggregationType;
import io.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import java.util.Arrays;

final class AgentInterpreterTestSupport {

  private AgentInterpreterTestSupport() {}

  /**
   * Builds an {@link ExecutionContext} backed by a {@link ProcessReportDataDto} configured with the
   * given aggregation types. The context is a Mockito mock because the real constructor requires a
   * fully populated {@code ReportEvaluationContext}, which the agent interpreters do not use — they
   * only read {@link ExecutionContext#getReportData()}.
   */
  static ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> contextWith(
      final AggregationType... aggregationTypes) {
    final ProcessReportDataDto reportData = new ProcessReportDataDto();
    final AggregationDto[] aggregations =
        Arrays.stream(aggregationTypes).map(AggregationDto::new).toArray(AggregationDto[]::new);
    reportData.getConfiguration().setAggregationTypes(aggregations);
    @SuppressWarnings("unchecked")
    final ExecutionContext<ProcessReportDataDto, ProcessExecutionPlan> ctx =
        mock(ExecutionContext.class);
    when(ctx.getReportData()).thenReturn(reportData);
    return ctx;
  }
}
