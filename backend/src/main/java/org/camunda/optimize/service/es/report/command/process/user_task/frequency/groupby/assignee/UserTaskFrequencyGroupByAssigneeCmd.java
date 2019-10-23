/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process.user_task.frequency.groupby.assignee;

import org.camunda.optimize.dto.optimize.query.report.ReportEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.service.es.report.command.Command;
import org.camunda.optimize.service.es.report.command.CommandContext;
import org.camunda.optimize.service.es.report.command.exec.ReportCmdExecutionPlan;
import org.camunda.optimize.service.es.report.command.exec.builder.ReportCmdExecutionPlanBuilder;
import org.camunda.optimize.service.es.report.command.modules.distributed_by.DistributedByNone;
import org.camunda.optimize.service.es.report.command.modules.group_by.GroupByAssignee;
import org.camunda.optimize.service.es.report.command.modules.view.frequency.CountUserTaskFrequencyView;
import org.camunda.optimize.service.es.report.result.process.SingleProcessMapReportResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UserTaskFrequencyGroupByAssigneeCmd
  implements Command<SingleProcessReportDefinitionDto> {

  private final ReportCmdExecutionPlan<ReportMapResultDto> executionPlan;

  @Autowired
  public UserTaskFrequencyGroupByAssigneeCmd(final ReportCmdExecutionPlanBuilder builder) {
    this.executionPlan = builder.createExecutionPlan()
      .view(CountUserTaskFrequencyView.class)
      .groupBy(GroupByAssignee.class)
      .distributedBy(DistributedByNone.class)
      .resultAsMap()
      .build();
  }

  @Override
  public ReportEvaluationResult evaluate(final CommandContext<SingleProcessReportDefinitionDto> commandContext) {
    final ReportMapResultDto evaluate = executionPlan.evaluate(commandContext.getReportDefinition().getData());
    return new SingleProcessMapReportResult(evaluate, commandContext.getReportDefinition());
  }

  @Override
  public String createCommandKey() {
    return executionPlan.generateCommandKey();
  }
}
