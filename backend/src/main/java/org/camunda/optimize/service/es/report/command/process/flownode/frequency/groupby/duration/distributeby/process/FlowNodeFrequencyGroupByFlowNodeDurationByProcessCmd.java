/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.process.flownode.frequency.groupby.duration.distributeby.process;

import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
import org.camunda.optimize.service.es.report.command.ProcessCmd;
import org.camunda.optimize.service.es.report.command.exec.ProcessReportCmdExecutionPlan;
import org.camunda.optimize.service.es.report.command.exec.builder.ReportCmdExecutionPlanBuilder;
import org.camunda.optimize.service.es.report.command.modules.distributed_by.process.ProcessDistributedByProcess;
import org.camunda.optimize.service.es.report.command.modules.group_by.process.flownode.ProcessGroupByFlowNodeDuration;
import org.camunda.optimize.service.es.report.command.modules.view.process.frequency.ProcessViewFlowNodeFrequency;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FlowNodeFrequencyGroupByFlowNodeDurationByProcessCmd extends ProcessCmd<List<HyperMapResultEntryDto>> {

  public FlowNodeFrequencyGroupByFlowNodeDurationByProcessCmd(final ReportCmdExecutionPlanBuilder builder) {
    super(builder);
  }

  @Override
  protected ProcessReportCmdExecutionPlan<List<HyperMapResultEntryDto>> buildExecutionPlan(final ReportCmdExecutionPlanBuilder builder) {
    return builder.createExecutionPlan()
      .processCommand()
      .view(ProcessViewFlowNodeFrequency.class)
      .groupBy(ProcessGroupByFlowNodeDuration.class)
      .distributedBy(ProcessDistributedByProcess.class)
      .resultAsHyperMap()
      .build();
  }

}
