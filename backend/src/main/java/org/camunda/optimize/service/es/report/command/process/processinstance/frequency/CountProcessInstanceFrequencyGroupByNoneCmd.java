/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process.processinstance.frequency;

import lombok.RequiredArgsConstructor;
import org.camunda.optimize.dto.optimize.query.report.ReportEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.result.NumberResultDto;
import org.camunda.optimize.service.es.report.command.Command;
import org.camunda.optimize.service.es.report.command.CommandContext;
import org.camunda.optimize.service.es.report.command.exec.ReportCmdExecutionPlan;
import org.camunda.optimize.service.es.report.command.exec.builder.ReportCmdExecutionPlanBuilder;
import org.camunda.optimize.service.es.report.command.modules.group_by.GroupByNone;
import org.camunda.optimize.service.es.report.command.modules.view.FrequencyCountView;
import org.camunda.optimize.service.es.report.result.process.SingleProcessNumberReportResult;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class CountProcessInstanceFrequencyGroupByNoneCmd
  implements Command<SingleProcessReportDefinitionDto> {

  private final ReportCmdExecutionPlanBuilder builder;

  @Override
  public ReportEvaluationResult evaluate(final CommandContext<SingleProcessReportDefinitionDto> commandContext) {
    final ReportCmdExecutionPlan<NumberResultDto> build = builder.createExecutionPlan()
      .groupBy(GroupByNone.class)
      .addViewPart(FrequencyCountView.class)
      .build();

    final NumberResultDto evaluate = build.evaluate(commandContext.getReportDefinition().getData());
    return new SingleProcessNumberReportResult(evaluate, commandContext.getReportDefinition());
  }
}
