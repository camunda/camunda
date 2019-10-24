/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.none;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.ReportEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.NumberResultDto;
import org.camunda.optimize.service.es.report.command.Command;
import org.camunda.optimize.service.es.report.command.CommandContext;
import org.camunda.optimize.service.es.report.command.exec.ProcessReportCmdExecutionPlan;
import org.camunda.optimize.service.es.report.command.exec.builder.ReportCmdExecutionPlanBuilder;
import org.camunda.optimize.service.es.report.command.modules.distributed_by.process.ProcessDistributedByNone;
import org.camunda.optimize.service.es.report.command.modules.group_by.process.ProcessGroupByNone;
import org.camunda.optimize.service.es.report.command.modules.view.process.duration.ProcessDurationViewOnProcessPart;
import org.camunda.optimize.service.es.report.result.process.SingleProcessNumberReportResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class ProcessInstanceDurationOnProcessPartGroupByNoneCmd implements Command<SingleProcessReportDefinitionDto> {

  private final ProcessReportCmdExecutionPlan<NumberResultDto> executionPlan;

  @Autowired
  public ProcessInstanceDurationOnProcessPartGroupByNoneCmd(final ReportCmdExecutionPlanBuilder builder) {
    this.executionPlan = builder.createExecutionPlan()
      .processCommand()
      .view(ProcessDurationViewOnProcessPart.class)
      .groupBy(ProcessGroupByNone.class)
      .distributedBy(ProcessDistributedByNone.class)
      .resultAsNumber()
      .build();
  }

  @Override
  public ReportEvaluationResult evaluate(final CommandContext<SingleProcessReportDefinitionDto> commandContext) {
    final NumberResultDto evaluate = this.executionPlan.evaluate(commandContext.getReportDefinition().getData());
    return new SingleProcessNumberReportResult(evaluate, commandContext.getReportDefinition());
  }

  @Override
  public String createCommandKey() {
    return this.executionPlan.generateCommandKey();
  }
}
