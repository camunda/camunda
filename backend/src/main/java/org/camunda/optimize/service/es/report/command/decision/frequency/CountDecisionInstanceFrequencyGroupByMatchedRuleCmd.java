/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.decision.frequency;

import org.camunda.optimize.dto.optimize.query.report.ReportEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.service.es.report.command.Command;
import org.camunda.optimize.service.es.report.command.CommandContext;
import org.camunda.optimize.service.es.report.command.exec.DecisionReportCmdExecutionPlan;
import org.camunda.optimize.service.es.report.command.exec.builder.ReportCmdExecutionPlanBuilder;
import org.camunda.optimize.service.es.report.command.modules.distributed_by.decision.DecisionDistributedByNone;
import org.camunda.optimize.service.es.report.command.modules.group_by.decision.DecisionGroupByMatchedRule;
import org.camunda.optimize.service.es.report.command.modules.view.decision.DecisionViewCountInstanceFrequency;
import org.camunda.optimize.service.es.report.result.decision.SingleDecisionMapReportResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class CountDecisionInstanceFrequencyGroupByMatchedRuleCmd
  implements Command<SingleDecisionReportDefinitionRequestDto> {

  private final DecisionReportCmdExecutionPlan<ReportMapResultDto> executionPlan;

  @Autowired
  public CountDecisionInstanceFrequencyGroupByMatchedRuleCmd(final ReportCmdExecutionPlanBuilder builder) {
    this.executionPlan = builder.createExecutionPlan()
      .decisionCommand()
      .view(DecisionViewCountInstanceFrequency.class)
      .groupBy(DecisionGroupByMatchedRule.class)
      .distributedBy(DecisionDistributedByNone.class)
      .resultAsMap()
      .build();
  }

  @Override
  public ReportEvaluationResult evaluate(final CommandContext<SingleDecisionReportDefinitionRequestDto> commandContext) {
    final ReportMapResultDto evaluate = executionPlan.evaluate(commandContext);
    return new SingleDecisionMapReportResult(evaluate, commandContext.getReportDefinition());
  }

  @Override
  public String createCommandKey() {
    return executionPlan.generateCommandKey();
  }
}
