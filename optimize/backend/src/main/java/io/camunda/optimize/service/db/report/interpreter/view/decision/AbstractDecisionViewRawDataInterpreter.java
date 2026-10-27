/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.interpreter.view.decision;

import static io.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto.INPUT_PREFIX;
import static io.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto.OUTPUT_PREFIX;
import static io.camunda.optimize.service.DefinitionService.prepareTenantListForDefinitionSearch;
import static io.camunda.optimize.service.db.report.plan.decision.DecisionView.DECISION_VIEW_RAW_DATA;
import static io.camunda.optimize.service.export.CSVUtils.extractAllDecisionInstanceDtoFieldKeys;
import static java.util.stream.Collectors.toList;

import io.camunda.optimize.dto.optimize.query.report.single.SingleReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.InputVariableEntry;
import io.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.OutputVariableEntry;
import io.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionInstanceDto;
import io.camunda.optimize.service.db.reader.DecisionVariableReader;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.interpreter.util.RawDecisionDataResultDtoMapper;
import io.camunda.optimize.service.db.report.plan.decision.DecisionExecutionPlan;
import io.camunda.optimize.service.db.report.plan.decision.DecisionView;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public abstract class AbstractDecisionViewRawDataInterpreter implements DecisionViewInterpreter {
  public static final String INPUT_VARIABLE_PREFIX = "inputVariable:";
  public static final String OUTPUT_VARIABLE_PREFIX = "outputVariable:";
  protected final RawDecisionDataResultDtoMapper rawDataSingleReportResultDtoMapper =
      new RawDecisionDataResultDtoMapper();

  @Override
  public Set<DecisionView> getSupportedViews() {
    return Set.of(DECISION_VIEW_RAW_DATA);
  }

  protected abstract DecisionVariableReader getDecisionVariableReader();

  protected Set<InputVariableEntry> getInputVariableEntries(
      final SingleReportDataDto reportDataDto) {
    return getDecisionVariableReader()
        .getInputVariableNames(
            reportDataDto.getDefinitionKey(),
            reportDataDto.getDefinitionVersions(),
            prepareTenantListForDefinitionSearch(reportDataDto.getTenantIds()))
        .stream()
        .map(
            inputVar ->
                new InputVariableEntry(
                    inputVar.getId(), inputVar.getName(), inputVar.getType(), null))
        .collect(Collectors.toSet());
  }

  protected Set<OutputVariableEntry> getOutputVars(final SingleReportDataDto reportDataDto) {
    return getDecisionVariableReader()
        .getOutputVariableNames(
            reportDataDto.getDefinitionKey(),
            reportDataDto.getDefinitionVersions(),
            prepareTenantListForDefinitionSearch(reportDataDto.getTenantIds()))
        .stream()
        .map(
            outputVar ->
                new OutputVariableEntry(
                    outputVar.getId(),
                    outputVar.getName(),
                    outputVar.getType(),
                    Collections.emptyList()))
        .collect(Collectors.toSet());
  }

  protected void addNewVariablesAndDtoFieldsToTableColumnConfig(
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> context,
      final List<RawDataDecisionInstanceDto> rawData) {
    final List<String> variableNames =
        rawData.stream()
            .flatMap(
                rawDataDecisionInstanceDto ->
                    rawDataDecisionInstanceDto.getInputVariables().values().stream())
            .map(this::getPrefixedInputVariableId)
            .collect(toList());
    variableNames.addAll(
        rawData.stream()
            .flatMap(
                rawDataDecisionInstanceDto ->
                    rawDataDecisionInstanceDto.getOutputVariables().values().stream())
            .map(this::getPrefixedOutputVariableId)
            .toList());

    final TableColumnDto tableColumns = context.getReportConfiguration().getTableColumns();
    tableColumns.addNewAndRemoveUnexpectedVariableColumns(variableNames);
    tableColumns.addDtoColumns(extractAllDecisionInstanceDtoFieldKeys());
  }

  protected String getPrefixedInputVariableId(final InputVariableEntry inputVariableEntry) {
    return INPUT_PREFIX + inputVariableEntry.getId();
  }

  protected String getPrefixedOutputVariableId(final OutputVariableEntry outputVariableEntry) {
    return OUTPUT_PREFIX + outputVariableEntry.getId();
  }
}
