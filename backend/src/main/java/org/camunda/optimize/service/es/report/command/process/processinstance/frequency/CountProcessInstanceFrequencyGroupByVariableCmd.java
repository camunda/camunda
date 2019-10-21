/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process.processinstance.frequency;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.ReportEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.ReportMapResultDto;
import org.camunda.optimize.service.es.report.command.Command;
import org.camunda.optimize.service.es.report.command.CommandContext;
import org.camunda.optimize.service.es.report.command.exec.ReportCmdExecutionPlan;
import org.camunda.optimize.service.es.report.command.exec.builder.ReportCmdExecutionPlanBuilder;
import org.camunda.optimize.service.es.report.command.modules.group_by.GroupByVariable;
import org.camunda.optimize.service.es.report.command.modules.view.FrequencyCountView;
import org.camunda.optimize.service.es.report.result.process.SingleProcessMapReportResult;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class CountProcessInstanceFrequencyGroupByVariableCmd implements Command<SingleProcessReportDefinitionDto> {

  private final ReportCmdExecutionPlanBuilder builder;

  @Override
  public ReportEvaluationResult evaluate(final CommandContext<SingleProcessReportDefinitionDto> commandContext) {
    final ReportCmdExecutionPlan<ReportMapResultDto> build = builder.createExecutionPlan()
      .groupBy(GroupByVariable.class)
      .addViewPart(FrequencyCountView.class)
      .build();

    final ReportMapResultDto evaluate = build.evaluate(commandContext.getReportDefinition().getData());
    return new SingleProcessMapReportResult(evaluate, commandContext.getReportDefinition());
  }

}
