/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process.flownode.frequency.groupby.flownode;

import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.MapResultEntryDto;
import org.camunda.optimize.service.es.report.command.ProcessCmd;
import org.camunda.optimize.service.es.report.command.exec.ProcessReportCmdExecutionPlan;
import org.camunda.optimize.service.es.report.command.exec.builder.ReportCmdExecutionPlanBuilder;
import org.camunda.optimize.service.es.report.command.modules.distributed_by.process.ProcessDistributedByNone;
import org.camunda.optimize.service.es.report.command.modules.group_by.process.flownode.ProcessGroupByFlowNodeDuration;
import org.camunda.optimize.service.es.report.command.modules.view.process.frequency.ProcessViewCountFlowNodeFrequency;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FlowNodeFrequencyGroupByFlowNodeDurationCmd extends ProcessCmd<List<MapResultEntryDto>> {
  public FlowNodeFrequencyGroupByFlowNodeDurationCmd(final ReportCmdExecutionPlanBuilder builder) {
    super(builder);
  }

  @Override
  protected ProcessReportCmdExecutionPlan<List<MapResultEntryDto>> buildExecutionPlan(final ReportCmdExecutionPlanBuilder builder) {
    return builder.createExecutionPlan()
      .processCommand()
      .view(ProcessViewCountFlowNodeFrequency.class)
      .groupBy(ProcessGroupByFlowNodeDuration.class)
      .distributedBy(ProcessDistributedByNone.class)
      .resultAsMap()
      .build();
  }

}
