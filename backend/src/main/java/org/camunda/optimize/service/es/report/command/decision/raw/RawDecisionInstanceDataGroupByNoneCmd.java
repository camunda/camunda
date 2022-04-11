/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.es.report.command.decision.raw;

import org.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import org.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.InputVariableEntry;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.OutputVariableEntry;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionInstanceDto;
import org.camunda.optimize.service.es.report.ReportEvaluationContext;
import org.camunda.optimize.service.es.report.command.DecisionCmd;
import org.camunda.optimize.service.es.report.command.exec.DecisionReportCmdExecutionPlan;
import org.camunda.optimize.service.es.report.command.exec.builder.ReportCmdExecutionPlanBuilder;
import org.camunda.optimize.service.es.report.command.modules.distributed_by.decision.DecisionDistributedByNone;
import org.camunda.optimize.service.es.report.command.modules.group_by.decision.DecisionGroupByNone;
import org.camunda.optimize.service.es.report.command.modules.view.decision.DecisionViewRawData;
import org.springframework.stereotype.Component;

import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto.INPUT_PREFIX;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto.OUTPUT_PREFIX;
import static org.camunda.optimize.service.export.CSVUtils.extractAllDecisionInstanceDtoFieldKeys;
import static org.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;

@Component
public class RawDecisionInstanceDataGroupByNoneCmd extends DecisionCmd<List<RawDataDecisionInstanceDto>> {

  public RawDecisionInstanceDataGroupByNoneCmd(final ReportCmdExecutionPlanBuilder builder) {
    super(builder);
  }

  @Override
  protected DecisionReportCmdExecutionPlan<List<RawDataDecisionInstanceDto>> buildExecutionPlan(
    final ReportCmdExecutionPlanBuilder builder) {
    return builder.createExecutionPlan()
      .decisionCommand()
      .view(DecisionViewRawData.class)
      .groupBy(DecisionGroupByNone.class)
      .distributedBy(DecisionDistributedByNone.class)
      .<RawDataDecisionInstanceDto>resultAsRawData()
      .build();
  }

  @Override
  public CommandEvaluationResult<List<RawDataDecisionInstanceDto>> evaluate(
    final ReportEvaluationContext<SingleDecisionReportDefinitionRequestDto> reportEvaluationContext) {
    final CommandEvaluationResult<List<RawDataDecisionInstanceDto>> commandResult =
      super.evaluate(reportEvaluationContext);
    addNewVariablesAndDtoFieldsToTableColumnConfig(reportEvaluationContext, commandResult);
    return commandResult;
  }

  @SuppressWarnings(UNCHECKED_CAST)
  private void addNewVariablesAndDtoFieldsToTableColumnConfig(
    final ReportEvaluationContext<SingleDecisionReportDefinitionRequestDto> reportEvaluationContext,
    final CommandEvaluationResult<List<RawDataDecisionInstanceDto>> result) {
    final List<String> variableNames = result.getFirstMeasureData()
      .stream()
      .flatMap(rawDataDecisionInstanceDto -> rawDataDecisionInstanceDto.getInputVariables().values().stream())
      .map(this::getPrefixedInputVariableId)
      .collect(toList());
    variableNames.addAll(
      ((List<RawDataDecisionInstanceDto>) result.getFirstMeasureData())
        .stream()
        .flatMap(rawDataDecisionInstanceDto -> rawDataDecisionInstanceDto.getOutputVariables().values().stream())
        .map(this::getPrefixedOutputVariableId)
        .collect(toList())
    );

    TableColumnDto tableColumns = reportEvaluationContext.getReportDefinition()
      .getData()
      .getConfiguration()
      .getTableColumns();
    tableColumns.addNewAndRemoveUnexpectedVariableColumns(variableNames);
    tableColumns.addDtoColumns(extractAllDecisionInstanceDtoFieldKeys());
  }

  private String getPrefixedInputVariableId(final InputVariableEntry inputVariableEntry) {
    return INPUT_PREFIX + inputVariableEntry.getId();
  }

  private String getPrefixedOutputVariableId(final OutputVariableEntry outputVariableEntry) {
    return OUTPUT_PREFIX + outputVariableEntry.getId();
  }

}
