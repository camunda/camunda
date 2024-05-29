/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
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
