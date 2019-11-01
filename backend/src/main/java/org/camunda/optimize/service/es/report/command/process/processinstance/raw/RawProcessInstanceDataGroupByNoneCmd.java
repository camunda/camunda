/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process.processinstance.raw;

import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.raw.RawDataProcessReportResultDto;
import org.camunda.optimize.service.es.report.command.Command;
import org.camunda.optimize.service.es.report.command.CommandContext;
import org.camunda.optimize.service.es.report.command.exec.ProcessReportCmdExecutionPlan;
import org.camunda.optimize.service.es.report.command.exec.builder.ReportCmdExecutionPlanBuilder;
import org.camunda.optimize.service.es.report.command.modules.distributed_by.process.ProcessDistributedByNone;
import org.camunda.optimize.service.es.report.command.modules.group_by.process.ProcessGroupByNone;
import org.camunda.optimize.service.es.report.command.modules.view.process.ProcessViewRawData;
import org.camunda.optimize.service.es.report.result.process.SingleProcessRawDataReportResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RawProcessInstanceDataGroupByNoneCmd
  implements Command<SingleProcessReportDefinitionDto> {

  private final ProcessReportCmdExecutionPlan<RawDataProcessReportResultDto> executionPlan;

  @Autowired
  public RawProcessInstanceDataGroupByNoneCmd(final ReportCmdExecutionPlanBuilder builder) {
    this.executionPlan = builder.createExecutionPlan()
      .processCommand()
      .view(ProcessViewRawData.class)
      .groupBy(ProcessGroupByNone.class)
      .distributedBy(ProcessDistributedByNone.class)
      .resultAsRawData()
      .build();
  }

  @Override
  public SingleProcessRawDataReportResult evaluate(final CommandContext<SingleProcessReportDefinitionDto> commandContext) {
    final RawDataProcessReportResultDto evaluate = executionPlan.evaluate(commandContext);
    return new SingleProcessRawDataReportResult(evaluate, commandContext.getReportDefinition());
  }

  @Override
  public String createCommandKey() {
    return executionPlan.generateCommandKey();
  }
}
