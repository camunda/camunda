/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command;

import io.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import io.camunda.optimize.service.db.es.report.MinMaxStatDto;
import io.camunda.optimize.service.db.es.report.ReportEvaluationContext;
import io.camunda.optimize.service.db.es.report.command.exec.ExecutionContext;
import io.camunda.optimize.service.db.es.report.command.exec.ProcessReportCmdExecutionPlan;
import io.camunda.optimize.service.db.es.report.command.exec.builder.ReportCmdExecutionPlanBuilder;
import java.util.Optional;
import org.elasticsearch.index.query.BoolQueryBuilder;

public abstract class ProcessCmd<T> implements Command<T, SingleProcessReportDefinitionRequestDto> {

  protected final ProcessReportCmdExecutionPlan<T> executionPlan;

  protected ProcessCmd(final ReportCmdExecutionPlanBuilder builder) {
    this.executionPlan = buildExecutionPlan(builder);
  }

  @Override
  public CommandEvaluationResult<T> evaluate(
      final ReportEvaluationContext<SingleProcessReportDefinitionRequestDto>
          reportEvaluationContext) {
    return executionPlan.evaluate(reportEvaluationContext);
  }

  protected abstract ProcessReportCmdExecutionPlan<T> buildExecutionPlan(
      final ReportCmdExecutionPlanBuilder builder);

  public BoolQueryBuilder getBaseQuery(
      final ReportEvaluationContext<SingleProcessReportDefinitionRequestDto>
          reportEvaluationContext) {
    return executionPlan.setupBaseQuery(new ExecutionContext<>(reportEvaluationContext));
  }

  @Override
  public Optional<MinMaxStatDto> getGroupByMinMaxStats(
      final ReportEvaluationContext<SingleProcessReportDefinitionRequestDto>
          reportEvaluationContext) {
    return executionPlan.getGroupByMinMaxStats(new ExecutionContext<>(reportEvaluationContext));
  }

  @Override
  public String createCommandKey() {
    return executionPlan.generateCommandKey();
  }
}
