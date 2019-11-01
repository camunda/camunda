/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.decision.raw;

import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionReportResultDto;
import org.camunda.optimize.service.es.report.command.Command;
import org.camunda.optimize.service.es.report.command.CommandContext;
import org.camunda.optimize.service.es.report.command.exec.DecisionReportCmdExecutionPlan;
import org.camunda.optimize.service.es.report.command.exec.builder.ReportCmdExecutionPlanBuilder;
import org.camunda.optimize.service.es.report.command.modules.distributed_by.decision.DecisionDistributedByNone;
import org.camunda.optimize.service.es.report.command.modules.group_by.decision.DecisionGroupByNone;
import org.camunda.optimize.service.es.report.command.modules.view.decision.DecisionViewRawData;
import org.camunda.optimize.service.es.report.result.decision.SingleDecisionRawDataReportResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RawDecisionInstanceDataGroupByNoneCmd
  implements Command<SingleDecisionReportDefinitionDto> {

  private final DecisionReportCmdExecutionPlan<RawDataDecisionReportResultDto> executionPlan;

  @Autowired
  public RawDecisionInstanceDataGroupByNoneCmd(final ReportCmdExecutionPlanBuilder builder) {
    this.executionPlan = builder.createExecutionPlan()
      .decisionCommand()
      .view(DecisionViewRawData.class)
      .groupBy(DecisionGroupByNone.class)
      .distributedBy(DecisionDistributedByNone.class)
      .resultAsRawData()
      .build();
  }

  @Override
  public SingleDecisionRawDataReportResult evaluate(final CommandContext<SingleDecisionReportDefinitionDto> commandContext) {
    final RawDataDecisionReportResultDto evaluate = executionPlan.evaluate(commandContext);
    return new SingleDecisionRawDataReportResult(evaluate, commandContext.getReportDefinition());
  }

  @Override
  public String createCommandKey() {
    return executionPlan.generateCommandKey();
  }
}
