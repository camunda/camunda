/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.process.incident.frequency;

import org.camunda.optimize.service.es.report.command.ProcessCmd;
import org.camunda.optimize.service.es.report.command.exec.ProcessReportCmdExecutionPlan;
import org.camunda.optimize.service.es.report.command.exec.builder.ReportCmdExecutionPlanBuilder;
import org.camunda.optimize.service.es.report.command.modules.distributed_by.process.ProcessDistributedByNone;
import org.camunda.optimize.service.es.report.command.modules.group_by.process.none.ProcessIncidentGroupByNone;
import org.camunda.optimize.service.es.report.command.modules.view.process.frequency.ProcessViewIncidentFrequency;
import org.springframework.stereotype.Component;

@Component
public class IncidentFrequencyGroupByNoneCmd extends ProcessCmd<Double> {

  public IncidentFrequencyGroupByNoneCmd(final ReportCmdExecutionPlanBuilder builder) {
    super(builder);
  }

  @Override
  protected ProcessReportCmdExecutionPlan<Double> buildExecutionPlan(final ReportCmdExecutionPlanBuilder builder) {
    return builder.createExecutionPlan()
      .processCommand()
      .view(ProcessViewIncidentFrequency.class)
      .groupBy(ProcessIncidentGroupByNone.class)
      .distributedBy(ProcessDistributedByNone.class)
      .resultAsNumber()
      .build();
  }

}
