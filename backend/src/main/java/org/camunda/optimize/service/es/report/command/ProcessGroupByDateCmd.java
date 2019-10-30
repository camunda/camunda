/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command;

import org.camunda.optimize.dto.optimize.query.report.ReportEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.service.es.report.command.exec.ProcessGroupByDateReportCmdExecutionPlan;
import org.camunda.optimize.service.es.report.command.exec.ProcessReportCmdExecutionPlan;
import org.camunda.optimize.service.es.report.command.exec.builder.ReportCmdExecutionPlanBuilder;
import org.camunda.optimize.service.es.report.result.process.SingleProcessMapReportResult;
import org.elasticsearch.search.aggregations.metrics.stats.Stats;

import java.util.Optional;

public abstract class ProcessGroupByDateCmd implements Command<SingleProcessReportDefinitionDto> {

  private final ProcessGroupByDateReportCmdExecutionPlan executionPlan;

  public ProcessGroupByDateCmd(final ReportCmdExecutionPlanBuilder builder) {
    this.executionPlan = new ProcessGroupByDateReportCmdExecutionPlan(buildExecutionPlan(builder));
  }

  protected abstract ProcessReportCmdExecutionPlan<ReportMapResultDto> buildExecutionPlan(final ReportCmdExecutionPlanBuilder builder);

  @Override
  public ReportEvaluationResult evaluate(final CommandContext<SingleProcessReportDefinitionDto> commandContext) {
    final ReportMapResultDto evaluate = executionPlan.evaluate(commandContext);
    return new SingleProcessMapReportResult(evaluate, commandContext.getReportDefinition());
  }

  public Optional<Stats> calculateGroupByDateRange(final ProcessReportDataDto reportDataDto) {
    return executionPlan.calculateGroupByDateRange(reportDataDto);
  }

  @Override
  public String createCommandKey() {
    return executionPlan.generateCommandKey();
  }
}
