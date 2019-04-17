/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.process.util;

import org.camunda.optimize.dto.optimize.ReportConstants;
import org.camunda.optimize.dto.optimize.importing.ProcessDefinitionOptimizeDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.ProcessReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.SingleProcessReportDefinitionDto;
import org.camunda.optimize.dto.optimize.query.report.single.process.result.ProcessReportMapResult;
import org.camunda.optimize.dto.optimize.query.report.single.result.MapResultEntryDto;
import org.camunda.optimize.service.es.report.command.CommandContext;
import org.camunda.optimize.service.es.report.result.ReportEvaluationResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class GroupByFlowNodeCommandUtil {
  private static final Logger logger = LoggerFactory.getLogger(GroupByFlowNodeCommandUtil.class);

  private GroupByFlowNodeCommandUtil() {
  }

  public static <MAP extends ProcessReportMapResult<V>, V> void filterResultData(
    final CommandContext<SingleProcessReportDefinitionDto> commandContext,
    final ReportEvaluationResult<MAP, SingleProcessReportDefinitionDto> evaluationResult) {

    final ProcessReportDataDto reportData = commandContext.getReportDefinition().getData();
    // if version is set to all, filter for only latest flow nodes
    if (ReportConstants.ALL_VERSIONS.equalsIgnoreCase(reportData.getProcessDefinitionVersion())) {
      getProcessDefinitionIfAvailable(commandContext, reportData)
        .ifPresent(processDefinition -> {
          final Map<String, String> flowNodeNames = processDefinition.getFlowNodeNames();

          final List<MapResultEntryDto<V>> collect = evaluationResult.getResultAsDto()
            .getData()
            .stream()
            .filter(resultEntry -> flowNodeNames.containsKey(resultEntry.getKey()))
            .collect(Collectors.toList());

          evaluationResult.getResultAsDto().setData(collect);
        });
    }
  }

  public static <MAP extends ProcessReportMapResult<V>, V> void enrichResultData(
    final CommandContext<SingleProcessReportDefinitionDto> commandContext,
    final ReportEvaluationResult<MAP, SingleProcessReportDefinitionDto> evaluationResult) {

    final ProcessReportDataDto reportData = commandContext.getReportDefinition().getData();
    getProcessDefinitionIfAvailable(commandContext, reportData)
      .ifPresent(processDefinition -> {
        final Map<String, String> flowNodeNames = processDefinition.getFlowNodeNames();

        evaluationResult.getResultAsDto().getData().forEach(entry -> entry.setLabel(flowNodeNames.get(entry.getKey())));
      });
  }

  private static Optional<ProcessDefinitionOptimizeDto> getProcessDefinitionIfAvailable(
    final CommandContext<SingleProcessReportDefinitionDto> commandContext,
    final ProcessReportDataDto reportData) {

    return commandContext
      .getProcessDefinitionReader()
      .getFullyImportedProcessDefinitionAsService(
        reportData.getProcessDefinitionKey(), reportData.getProcessDefinitionVersion()
      );
  }

}
