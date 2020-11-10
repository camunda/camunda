/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process.flownode.duration.groupby.date.distributed_by.flownode;

import org.camunda.optimize.dto.optimize.query.report.ReportEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.ReportHyperMapResultDto;
import org.camunda.optimize.service.es.report.command.CommandContext;
import org.camunda.optimize.service.es.report.command.ProcessCmd;
import org.camunda.optimize.service.es.report.command.exec.ProcessReportCmdExecutionPlan;
import org.camunda.optimize.service.es.report.command.exec.builder.ReportCmdExecutionPlanBuilder;
import org.camunda.optimize.service.es.report.command.modules.distributed_by.process.ProcessDistributedByFlowNode;
import org.camunda.optimize.service.es.report.command.modules.group_by.process.date.ProcessGroupByFlowNodeStartDate;
import org.camunda.optimize.service.es.report.command.modules.view.process.duration.ProcessViewFlowNodeDuration;
import org.camunda.optimize.service.es.report.result.process.SingleProcessHyperMapReportResult;
import org.springframework.stereotype.Component;

@Component
public class FlowNodeDurationGroupByFlowNodeStartDateByFlowNodeCmd extends ProcessCmd<ReportHyperMapResultDto> {

  public FlowNodeDurationGroupByFlowNodeStartDateByFlowNodeCmd(final ReportCmdExecutionPlanBuilder builder) {
    super(builder);
  }

  @Override
  protected ProcessReportCmdExecutionPlan<ReportHyperMapResultDto> buildExecutionPlan(final ReportCmdExecutionPlanBuilder builder) {
    return builder.createExecutionPlan()
      .processCommand()
      .view(ProcessViewFlowNodeDuration.class)
      .groupBy(ProcessGroupByFlowNodeStartDate.class)
      .distributedBy(ProcessDistributedByFlowNode.class)
      .resultAsHyperMap()
      .build();
  }

  @Override
  public ReportEvaluationResult evaluate(final CommandContext<SingleProcessReportDefinitionRequestDto> commandContext) {
    final ReportHyperMapResultDto evaluate = executionPlan.evaluate(commandContext);
    return new SingleProcessHyperMapReportResult(evaluate, commandContext.getReportDefinition());
  }

  @Override
  public String createCommandKey() {
    return executionPlan.generateCommandKey();
  }

}
