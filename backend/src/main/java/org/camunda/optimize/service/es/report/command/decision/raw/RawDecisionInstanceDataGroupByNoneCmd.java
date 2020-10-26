/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.decision.raw;

import org.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.SingleDecisionReportDefinitionRequestDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.InputVariableEntry;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.OutputVariableEntry;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionReportResultDto;
import org.camunda.optimize.service.es.report.command.Command;
import org.camunda.optimize.service.es.report.command.CommandContext;
import org.camunda.optimize.service.es.report.command.exec.DecisionReportCmdExecutionPlan;
import org.camunda.optimize.service.es.report.command.exec.builder.ReportCmdExecutionPlanBuilder;
import org.camunda.optimize.service.es.report.command.modules.distributed_by.decision.DecisionDistributedByNone;
import org.camunda.optimize.service.es.report.command.modules.group_by.decision.DecisionGroupByNone;
import org.camunda.optimize.service.es.report.command.modules.view.decision.DecisionViewRawData;
import org.camunda.optimize.service.es.report.result.decision.SingleDecisionRawDataReportResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

import static java.util.stream.Collectors.toList;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto.INPUT_PREFIX;
import static org.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto.OUTPUT_PREFIX;
import static org.camunda.optimize.service.export.CSVUtils.extractAllDecisionInstanceDtoFieldKeys;

@Component
public class RawDecisionInstanceDataGroupByNoneCmd
  implements Command<SingleDecisionReportDefinitionRequestDto> {

  private final DecisionReportCmdExecutionPlan<RawDataDecisionReportResultDto> executionPlan;

  @Autowired
  public RawDecisionInstanceDataGroupByNoneCmd(final ReportCmdExecutionPlanBuilder builder) {
    this.executionPlan = builder.createExecutionPlan()
      .decisionCommand()
      .view(DecisionViewRawData.class)
      .groupBy(DecisionGroupByNone.class)
      .distributedBy(DecisionDistributedByNone.class)
      .resultAsRawData()
      .build();
  }

  @Override
  public SingleDecisionRawDataReportResult evaluate(final CommandContext<SingleDecisionReportDefinitionRequestDto> commandContext) {
    final RawDataDecisionReportResultDto evaluate = executionPlan.evaluate(commandContext);
    addNewVariablesAndDtoFieldsToTableColumnConfig(commandContext, evaluate);
    return new SingleDecisionRawDataReportResult(evaluate, commandContext.getReportDefinition());
  }

  @Override
  public String createCommandKey() {
    return executionPlan.generateCommandKey();
  }

  private void addNewVariablesAndDtoFieldsToTableColumnConfig(final CommandContext<SingleDecisionReportDefinitionRequestDto> commandContext,
                                                              final RawDataDecisionReportResultDto result) {
    final List<String> variableNames = result.getData()
      .stream()
      .flatMap(rawDataDecisionInstanceDto -> rawDataDecisionInstanceDto.getInputVariables().values().stream())
      .map(this::getPrefixedInputVariableId)
      .collect(toList());
    variableNames.addAll(
      result.getData()
        .stream()
        .flatMap(rawDataDecisionInstanceDto -> rawDataDecisionInstanceDto.getOutputVariables().values().stream())
        .map(this::getPrefixedOutputVariableId)
        .collect(toList())
    );

    TableColumnDto tableColumns = commandContext.getReportDefinition()
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
