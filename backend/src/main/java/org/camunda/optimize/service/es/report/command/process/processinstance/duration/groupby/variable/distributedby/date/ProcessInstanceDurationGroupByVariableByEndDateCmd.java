/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.variable.distributedby.date;

import org.camunda.optimize.dto.optimize.query.report.ReportEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.ReportHyperMapResultDto;
import org.camunda.optimize.service.es.report.command.CommandContext;
import org.camunda.optimize.service.es.report.command.ProcessCmd;
import org.camunda.optimize.service.es.report.command.exec.ProcessReportCmdExecutionPlan;
import org.camunda.optimize.service.es.report.command.exec.builder.ReportCmdExecutionPlanBuilder;
import org.camunda.optimize.service.es.report.command.modules.distributed_by.process.ProcessDistributedByInstanceEndDate;
import org.camunda.optimize.service.es.report.command.modules.group_by.process.ProcessGroupByVariable;
import org.camunda.optimize.service.es.report.command.modules.view.process.duration.ProcessViewInstanceDuration;
import org.camunda.optimize.service.es.report.result.process.SingleProcessHyperMapReportResult;
import org.springframework.stereotype.Component;

@Component
public class ProcessInstanceDurationGroupByVariableByEndDateCmd extends ProcessCmd<ReportHyperMapResultDto> {

  public ProcessInstanceDurationGroupByVariableByEndDateCmd(final ReportCmdExecutionPlanBuilder builder) {
    super(builder);
  }

  @Override
  public ReportEvaluationResult<?, SingleProcessReportDefinitionRequestDto> evaluate(
    final CommandContext<SingleProcessReportDefinitionRequestDto> commandContext) {
    final ReportHyperMapResultDto evaluate = executionPlan.evaluate(commandContext);
    return new SingleProcessHyperMapReportResult(evaluate, commandContext.getReportDefinition());
  }

  @Override
  protected ProcessReportCmdExecutionPlan<ReportHyperMapResultDto> buildExecutionPlan(
    final ReportCmdExecutionPlanBuilder builder) {
    return builder.createExecutionPlan()
      .processCommand()
      .view(ProcessViewInstanceDuration.class)
      .groupBy(ProcessGroupByVariable.class)
      .distributedBy(ProcessDistributedByInstanceEndDate.class)
      .resultAsHyperMap()
      .build();
  }
}
