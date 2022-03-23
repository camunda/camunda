/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command;

import org.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.service.es.report.MinMaxStatDto;
import org.camunda.optimize.service.es.report.ReportEvaluationContext;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.exec.ProcessReportCmdExecutionPlan;
import org.camunda.optimize.service.es.report.command.exec.builder.ReportCmdExecutionPlanBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;

import java.util.Optional;

public abstract class ProcessCmd<T> implements Command<T, SingleProcessReportDefinitionRequestDto> {

  protected final ProcessReportCmdExecutionPlan<T> executionPlan;

  protected ProcessCmd(final ReportCmdExecutionPlanBuilder builder) {
    this.executionPlan = buildExecutionPlan(builder);
  }

  @Override
  public CommandEvaluationResult<T> evaluate(final ReportEvaluationContext<SingleProcessReportDefinitionRequestDto> reportEvaluationContext) {
    return executionPlan.evaluate(reportEvaluationContext);
  }

  protected abstract ProcessReportCmdExecutionPlan<T> buildExecutionPlan(final ReportCmdExecutionPlanBuilder builder);

  public BoolQueryBuilder getBaseQuery(final ReportEvaluationContext<SingleProcessReportDefinitionRequestDto> reportEvaluationContext) {
    return executionPlan.setupBaseQuery(new ExecutionContext<>(reportEvaluationContext));
  }

  @Override
  public Optional<MinMaxStatDto> getGroupByMinMaxStats(final ReportEvaluationContext<SingleProcessReportDefinitionRequestDto> reportEvaluationContext) {
    return executionPlan.getGroupByMinMaxStats(new ExecutionContext<>(reportEvaluationContext));
  }

  @Override
  public String createCommandKey() {
    return executionPlan.generateCommandKey();
  }
}
