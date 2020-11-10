/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.date.distributedby.none;

import org.camunda.optimize.dto.optimize.query.report.ReportEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.service.es.report.command.CommandContext;
import org.camunda.optimize.service.es.report.command.ProcessCmd;
import org.camunda.optimize.service.es.report.command.exec.ProcessReportCmdExecutionPlan;
import org.camunda.optimize.service.es.report.command.exec.builder.ReportCmdExecutionPlanBuilder;
import org.camunda.optimize.service.es.report.command.modules.distributed_by.process.ProcessDistributedByNone;
import org.camunda.optimize.service.es.report.command.modules.group_by.process.date.ProcessGroupByProcessInstanceEndDate;
import org.camunda.optimize.service.es.report.command.modules.view.process.duration.ProcessViewInstanceDuration;
import org.camunda.optimize.service.es.report.result.process.SingleProcessMapReportResult;
import org.springframework.stereotype.Component;

@Component
public class ProcessInstanceDurationGroupByEndDateCmd extends ProcessCmd<ReportMapResultDto> {

  public ProcessInstanceDurationGroupByEndDateCmd(final ReportCmdExecutionPlanBuilder builder) {
    super(builder);
  }

  @Override
  public ReportEvaluationResult evaluate(final CommandContext<SingleProcessReportDefinitionRequestDto> commandContext) {
    final ReportMapResultDto evaluate = this.executionPlan.evaluate(commandContext);
    return new SingleProcessMapReportResult(evaluate, commandContext.getReportDefinition());
  }

  @Override
  protected ProcessReportCmdExecutionPlan<ReportMapResultDto> buildExecutionPlan(final ReportCmdExecutionPlanBuilder builder) {
    return builder.createExecutionPlan()
      .processCommand()
      .view(ProcessViewInstanceDuration.class)
      .groupBy(ProcessGroupByProcessInstanceEndDate.class)
      .distributedBy(ProcessDistributedByNone.class)
      .resultAsMap()
    .build();
  }
}
