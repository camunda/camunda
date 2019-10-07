/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process.user_task;

import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.service.es.report.command.CommandContext;
import org.camunda.optimize.service.es.report.command.process.ProcessReportCommand;
import org.camunda.optimize.service.es.report.command.process.util.GroupByFlowNodeCommandUtil;
import org.camunda.optimize.service.es.report.command.util.MapResultSortingUtility;
import org.camunda.optimize.service.es.report.result.process.SingleProcessHyperMapReportResult;

public abstract class UserTaskDistributedByUserTaskCommand extends ProcessReportCommand<SingleProcessHyperMapReportResult> {

  @Override
  protected void sortResultData(final SingleProcessHyperMapReportResult evaluationResult) {
    ((ProcessReportDataDto) getReportData()).getConfiguration().getSorting().ifPresent(
      sorting -> evaluationResult.getResultAsDto()
        .getData()
        .forEach(groupByEntry -> MapResultSortingUtility.sortResultData(sorting, groupByEntry))
    );
  }

  @Override
  protected SingleProcessHyperMapReportResult enrichResultData(final CommandContext<SingleProcessReportDefinitionDto> commandContext,
                                                               final SingleProcessHyperMapReportResult evaluationResult) {
    GroupByFlowNodeCommandUtil.enrichResultData(
      commandContext, evaluationResult
    );
    return evaluationResult;
  }

  @Override
  protected SingleProcessHyperMapReportResult filterResultData(final CommandContext<SingleProcessReportDefinitionDto> commandContext,
                                                          final SingleProcessHyperMapReportResult evaluationResult) {
    GroupByFlowNodeCommandUtil.filterHyperMapResultData(commandContext, evaluationResult);
    return evaluationResult;
  }
}
