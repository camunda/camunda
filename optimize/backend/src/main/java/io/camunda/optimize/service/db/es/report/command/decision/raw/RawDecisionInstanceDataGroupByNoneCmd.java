/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.decision.raw;

import static io.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto.INPUT_PREFIX;
import static io.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto.OUTPUT_PREFIX;
import static io.camunda.optimize.service.export.CSVUtils.extractAllDecisionInstanceDtoFieldKeys;
import static io.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;
import static java.util.stream.Collectors.toList;

import io.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.InputVariableEntry;
import io.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.OutputVariableEntry;
import io.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionInstanceDto;
import io.camunda.optimize.service.db.es.report.ReportEvaluationContext;
import io.camunda.optimize.service.db.es.report.command.DecisionCmd;
import io.camunda.optimize.service.db.es.report.command.exec.DecisionReportCmdExecutionPlan;
import io.camunda.optimize.service.db.es.report.command.exec.builder.ReportCmdExecutionPlanBuilder;
import io.camunda.optimize.service.db.es.report.command.modules.distributed_by.decision.DecisionDistributedByNone;
import io.camunda.optimize.service.db.es.report.command.modules.group_by.decision.DecisionGroupByNone;
import io.camunda.optimize.service.db.es.report.command.modules.view.decision.DecisionViewRawData;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RawDecisionInstanceDataGroupByNoneCmd
    extends DecisionCmd<List<RawDataDecisionInstanceDto>> {

  public RawDecisionInstanceDataGroupByNoneCmd(final ReportCmdExecutionPlanBuilder builder) {
    super(builder);
  }

  @Override
  protected DecisionReportCmdExecutionPlan<List<RawDataDecisionInstanceDto>> buildExecutionPlan(
      final ReportCmdExecutionPlanBuilder builder) {
    return builder
        .createExecutionPlan()
        .decisionCommand()
        .view(DecisionViewRawData.class)
        .groupBy(DecisionGroupByNone.class)
        .distributedBy(DecisionDistributedByNone.class)
        .<RawDataDecisionInstanceDto>resultAsRawData()
        .build();
  }

  @Override
  public CommandEvaluationResult<List<RawDataDecisionInstanceDto>> evaluate(
      final ReportEvaluationContext<SingleDecisionReportDefinitionRequestDto>
          reportEvaluationContext) {
    final CommandEvaluationResult<List<RawDataDecisionInstanceDto>> commandResult =
        super.evaluate(reportEvaluationContext);
    addNewVariablesAndDtoFieldsToTableColumnConfig(reportEvaluationContext, commandResult);
    return commandResult;
  }

  @SuppressWarnings(UNCHECKED_CAST)
  private void addNewVariablesAndDtoFieldsToTableColumnConfig(
      final ReportEvaluationContext<SingleDecisionReportDefinitionRequestDto>
          reportEvaluationContext,
      final CommandEvaluationResult<List<RawDataDecisionInstanceDto>> result) {
    final List<String> variableNames =
        result.getFirstMeasureData().stream()
            .flatMap(
                rawDataDecisionInstanceDto ->
                    rawDataDecisionInstanceDto.getInputVariables().values().stream())
            .map(this::getPrefixedInputVariableId)
            .collect(toList());
    variableNames.addAll(
        ((List<RawDataDecisionInstanceDto>) result.getFirstMeasureData())
            .stream()
                .flatMap(
                    rawDataDecisionInstanceDto ->
                        rawDataDecisionInstanceDto.getOutputVariables().values().stream())
                .map(this::getPrefixedOutputVariableId)
                .collect(toList()));

    TableColumnDto tableColumns =
        reportEvaluationContext
            .getReportDefinition()
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
