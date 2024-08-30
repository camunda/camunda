/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command;

import io.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import io.camunda.optimize.service.db.es.report.ReportEvaluationContext;
import io.camunda.optimize.service.db.es.report.command.exec.DecisionReportCmdExecutionPlan;
import io.camunda.optimize.service.db.es.report.command.exec.builder.ReportCmdExecutionPlanBuilder;

public abstract class DecisionCmd<T>
    implements Command<T, SingleDecisionReportDefinitionRequestDto> {

  protected final DecisionReportCmdExecutionPlan<T> executionPlan;

  protected DecisionCmd(final ReportCmdExecutionPlanBuilder builder) {
    this.executionPlan = buildExecutionPlan(builder);
  }

  @Override
  public CommandEvaluationResult<T> evaluate(
      final ReportEvaluationContext<SingleDecisionReportDefinitionRequestDto>
          reportEvaluationContext) {
    return executionPlan.evaluate(reportEvaluationContext);
  }

  protected abstract DecisionReportCmdExecutionPlan<T> buildExecutionPlan(
      final ReportCmdExecutionPlanBuilder builder);

  @Override
  public String createCommandKey() {
    return executionPlan.generateCommandKey();
  }
}
