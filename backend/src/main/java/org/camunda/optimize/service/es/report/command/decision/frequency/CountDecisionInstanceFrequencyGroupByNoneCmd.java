/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.decision.frequency;

import org.camunda.optimize.dto.optimize.query.report.ReportEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.NumberResultDto;
import org.camunda.optimize.service.es.report.command.Command;
import org.camunda.optimize.service.es.report.command.CommandContext;
import org.camunda.optimize.service.es.report.command.exec.DecisionReportCmdExecutionPlan;
import org.camunda.optimize.service.es.report.command.exec.builder.ReportCmdExecutionPlanBuilder;
import org.camunda.optimize.service.es.report.command.modules.distributed_by.decision.DecisionDistributedByNone;
import org.camunda.optimize.service.es.report.command.modules.group_by.decision.DecisionGroupByNone;
import org.camunda.optimize.service.es.report.command.modules.view.decision.DecisionViewCountInstanceFrequency;
import org.camunda.optimize.service.es.report.result.decision.SingleDecisionNumberReportResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CountDecisionInstanceFrequencyGroupByNoneCmd
  implements Command<SingleDecisionReportDefinitionRequestDto> {

  private final DecisionReportCmdExecutionPlan<NumberResultDto> executionPlan;

  @Autowired
  public CountDecisionInstanceFrequencyGroupByNoneCmd(final ReportCmdExecutionPlanBuilder builder) {
    this.executionPlan = builder.createExecutionPlan()
      .decisionCommand()
      .view(DecisionViewCountInstanceFrequency.class)
      .groupBy(DecisionGroupByNone.class)
      .distributedBy(DecisionDistributedByNone.class)
      .resultAsNumber()
      .build();
  }

  @Override
  public ReportEvaluationResult evaluate(final CommandContext<SingleDecisionReportDefinitionRequestDto> commandContext) {
    final NumberResultDto evaluate = executionPlan.evaluate(commandContext);
    return new SingleDecisionNumberReportResult(evaluate, commandContext.getReportDefinition());
  }

  @Override
  public String createCommandKey() {
    return executionPlan.generateCommandKey();
  }
}
