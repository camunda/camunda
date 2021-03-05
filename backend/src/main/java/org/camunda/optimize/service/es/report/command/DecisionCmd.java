/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command;

import org.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.service.es.report.ReportEvaluationContext;
import org.camunda.optimize.service.es.report.command.exec.DecisionReportCmdExecutionPlan;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.exec.builder.ReportCmdExecutionPlanBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;

public abstract class DecisionCmd<T> implements Command<T, SingleDecisionReportDefinitionRequestDto> {

  protected final DecisionReportCmdExecutionPlan<T> executionPlan;

  protected DecisionCmd(final ReportCmdExecutionPlanBuilder builder) {
    this.executionPlan = buildExecutionPlan(builder);
  }

  @Override
  public CommandEvaluationResult<T> evaluate(final ReportEvaluationContext<SingleDecisionReportDefinitionRequestDto> reportEvaluationContext) {
    return executionPlan.evaluate(reportEvaluationContext);
  }

  protected abstract DecisionReportCmdExecutionPlan<T> buildExecutionPlan(final ReportCmdExecutionPlanBuilder builder);

  public BoolQueryBuilder getBaseQuery(final ReportEvaluationContext<SingleDecisionReportDefinitionRequestDto> reportEvaluationContext) {
    return executionPlan.setupBaseQuery(new ExecutionContext<>(reportEvaluationContext));
  }

  @Override
  public String createCommandKey() {
    return executionPlan.generateCommandKey();
  }
}
