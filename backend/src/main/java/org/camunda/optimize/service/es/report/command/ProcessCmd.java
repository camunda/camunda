/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command;

import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportResultDto;
import org.camunda.optimize.service.es.report.MinMaxStatDto;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;
import org.camunda.optimize.service.es.report.command.exec.ProcessReportCmdExecutionPlan;
import org.camunda.optimize.service.es.report.command.exec.builder.ReportCmdExecutionPlanBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;

import java.util.Optional;

public abstract class ProcessCmd<R extends ProcessReportResultDto>
  implements Command<SingleProcessReportDefinitionRequestDto> {

  protected final ProcessReportCmdExecutionPlan<R> executionPlan;

  public ProcessCmd(final ReportCmdExecutionPlanBuilder builder) {
    this.executionPlan = buildExecutionPlan(builder);
  }

  protected abstract ProcessReportCmdExecutionPlan<R> buildExecutionPlan(final ReportCmdExecutionPlanBuilder builder);

  public BoolQueryBuilder getBaseQuery(final CommandContext<SingleProcessReportDefinitionRequestDto> commandContext) {
    return executionPlan.setupBaseQuery(new ExecutionContext<>(commandContext));
  }

  @Override
  public Optional<MinMaxStatDto> getGroupByMinMaxStats(final CommandContext<SingleProcessReportDefinitionRequestDto> commandContext) {
    return executionPlan.getGroupByMinMaxStats(new ExecutionContext<>(commandContext));
  }

  @Override
  public String createCommandKey() {
    return executionPlan.generateCommandKey();
  }
}
