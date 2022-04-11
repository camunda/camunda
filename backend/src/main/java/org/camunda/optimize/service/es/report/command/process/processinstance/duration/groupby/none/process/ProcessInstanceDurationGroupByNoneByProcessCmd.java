/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.process.processinstance.duration.groupby.none.process;

import org.camunda.optimize.dto.optimize.query.report.single.result.hyper.HyperMapResultEntryDto;
import org.camunda.optimize.service.es.report.command.ProcessCmd;
import org.camunda.optimize.service.es.report.command.exec.ProcessReportCmdExecutionPlan;
import org.camunda.optimize.service.es.report.command.exec.builder.ReportCmdExecutionPlanBuilder;
import org.camunda.optimize.service.es.report.command.modules.distributed_by.process.ProcessDistributedByProcess;
import org.camunda.optimize.service.es.report.command.modules.group_by.process.none.ProcessGroupByNone;
import org.camunda.optimize.service.es.report.command.modules.view.process.duration.ProcessViewInstanceDuration;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProcessInstanceDurationGroupByNoneByProcessCmd extends ProcessCmd<List<HyperMapResultEntryDto>> {

  public ProcessInstanceDurationGroupByNoneByProcessCmd(final ReportCmdExecutionPlanBuilder builder) {
    super(builder);
  }

  protected ProcessReportCmdExecutionPlan<List<HyperMapResultEntryDto>> buildExecutionPlan(final ReportCmdExecutionPlanBuilder builder) {
    return builder.createExecutionPlan()
      .processCommand()
      .view(ProcessViewInstanceDuration.class)
      .groupBy(ProcessGroupByNone.class)
      .distributedBy(ProcessDistributedByProcess.class)
      .resultAsHyperMap()
      .build();
  }

}
