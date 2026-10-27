/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report;

import io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import io.camunda.optimize.service.db.report.plan.ExecutionPlan;
import io.camunda.optimize.service.db.report.plan.process.ProcessExecutionPlan;
import java.util.List;
import java.util.stream.Stream;

public abstract class CombinedReportInstanceCounter<A> {
  public abstract long count(
      final List<SingleProcessReportDefinitionRequestDto> singleReportDefinitions);

  protected abstract ExecutionPlanExtractor getExecutionPlanExtractor();

  protected abstract A getBaseQuery(
      ProcessExecutionPlan plan,
      ReportEvaluationContext<SingleProcessReportDefinitionRequestDto> context);

  private Stream<ReportEvaluationContext<SingleProcessReportDefinitionRequestDto>>
      getAllReportEvaluationContexts(
          final List<SingleProcessReportDefinitionRequestDto> singleReportDefinitions) {
    return singleReportDefinitions.stream()
        .map(
            reportDefinition -> {
              final ReportEvaluationContext<SingleProcessReportDefinitionRequestDto>
                  reportEvaluationContext = new ReportEvaluationContext<>();
              reportEvaluationContext.setReportDefinition(reportDefinition);
              return reportEvaluationContext;
            });
  }

  protected List<A> getAllBaseQueries(
      final List<SingleProcessReportDefinitionRequestDto> singleReportDefinitions) {
    return getAllReportEvaluationContexts(singleReportDefinitions)
        .map(
            reportEvaluationContext -> {
              final ExecutionPlan plan =
                  getExecutionPlanExtractor()
                      .extractExecutionPlans(reportEvaluationContext.getReportDefinition())
                      .get(0);
              if (plan instanceof final ProcessExecutionPlan processExecutionPlan) {
                return getBaseQuery(processExecutionPlan, reportEvaluationContext);
              } else {
                throw new UnsupportedOperationException(
                    "CombinedReportInstanceCounter.getAllBaseQueries is defined only for process execution plans. Provided: "
                        + plan);
              }
            })
        .toList();
  }
}
