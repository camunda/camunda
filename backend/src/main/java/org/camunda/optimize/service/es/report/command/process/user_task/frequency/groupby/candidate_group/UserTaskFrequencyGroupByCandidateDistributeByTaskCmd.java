/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process.user_task.frequency.groupby.candidate_group;

import org.camunda.optimize.dto.optimize.query.report.ReportEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.ReportHyperMapResultDto;
import org.camunda.optimize.service.es.report.command.Command;
import org.camunda.optimize.service.es.report.command.CommandContext;
import org.camunda.optimize.service.es.report.command.exec.ReportCmdExecutionPlan;
import org.camunda.optimize.service.es.report.command.exec.builder.ReportCmdExecutionPlanBuilder;
import org.camunda.optimize.service.es.report.command.modules.distributed_by.DistributedByUserTask;
import org.camunda.optimize.service.es.report.command.modules.group_by.GroupByCandidateGroup;
import org.camunda.optimize.service.es.report.command.modules.view.frequency.CountUserTaskFrequencyView;
import org.camunda.optimize.service.es.report.result.process.SingleProcessHyperMapReportResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class UserTaskFrequencyGroupByCandidateDistributeByTaskCmd
  implements Command<SingleProcessReportDefinitionDto> {

  private final ReportCmdExecutionPlan<ReportHyperMapResultDto> executionPlan;

  @Autowired
  public UserTaskFrequencyGroupByCandidateDistributeByTaskCmd(final ReportCmdExecutionPlanBuilder builder) {
    this.executionPlan = builder.createExecutionPlan()
      .view(CountUserTaskFrequencyView.class)
      .groupBy(GroupByCandidateGroup.class)
      .distributedBy(DistributedByUserTask.class)
      .resultAsHyperMap()
      .build();
  }

  @Override
  public ReportEvaluationResult evaluate(final CommandContext<SingleProcessReportDefinitionDto> commandContext) {
    final ReportHyperMapResultDto evaluate = executionPlan.evaluate(commandContext.getReportDefinition().getData());
    return new SingleProcessHyperMapReportResult(evaluate, commandContext.getReportDefinition());
  }

  @Override
  public String createCommandKey() {
    return executionPlan.generateCommandKey();
  }
}
