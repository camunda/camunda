/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process.user_task.duration.groupby.date.distributed_by.none;

import org.camunda.optimize.dto.optimize.query.report.ReportEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.service.es.report.command.CommandContext;
import org.camunda.optimize.service.es.report.command.ProcessCmd;
import org.camunda.optimize.service.es.report.command.exec.ProcessReportCmdExecutionPlan;
import org.camunda.optimize.service.es.report.command.exec.builder.ReportCmdExecutionPlanBuilder;
import org.camunda.optimize.service.es.report.command.modules.distributed_by.process.ProcessDistributedByNone;
import org.camunda.optimize.service.es.report.command.modules.group_by.process.date.ProcessGroupByUserTaskStartDate;
import org.camunda.optimize.service.es.report.command.modules.view.process.duration.ProcessViewUserTaskDuration;
import org.camunda.optimize.service.es.report.result.process.SingleProcessMapReportResult;
import org.springframework.stereotype.Component;

@Component
public class UserTaskDurationGroupByUserTaskStartDateCmd extends ProcessCmd<ReportMapResultDto> {

  public UserTaskDurationGroupByUserTaskStartDateCmd(final ReportCmdExecutionPlanBuilder builder) {
    super(builder);
  }

  @Override
  protected ProcessReportCmdExecutionPlan<ReportMapResultDto> buildExecutionPlan(final ReportCmdExecutionPlanBuilder builder) {
    return builder.createExecutionPlan()
      .processCommand()
      .view(ProcessViewUserTaskDuration.class)
      .groupBy(ProcessGroupByUserTaskStartDate.class)
      .distributedBy(ProcessDistributedByNone.class)
      .resultAsMap()
      .build();
  }

  @Override
  public ReportEvaluationResult evaluate(final CommandContext<SingleProcessReportDefinitionRequestDto> commandContext) {
    final ReportMapResultDto evaluate = executionPlan.evaluate(commandContext);
    return new SingleProcessMapReportResult(evaluate, commandContext.getReportDefinition());
  }
}
