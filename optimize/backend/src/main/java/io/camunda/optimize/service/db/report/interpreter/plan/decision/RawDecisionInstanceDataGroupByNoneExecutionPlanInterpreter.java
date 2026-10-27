/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.report.interpreter.plan.decision;

import static io.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto.INPUT_PREFIX;
import static io.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto.OUTPUT_PREFIX;
import static io.camunda.optimize.service.db.report.plan.decision.DecisionExecutionPlan.DECISION_RAW_DECISION_INSTANCE_DATA_GROUP_BY_NONE;
import static io.camunda.optimize.service.export.CSVUtils.extractAllDecisionInstanceDtoFieldKeys;
import static io.camunda.optimize.util.SuppressionConstants.UNCHECKED_CAST;
import static java.util.stream.Collectors.toList;

import io.camunda.optimize.dto.optimize.query.report.CommandEvaluationResult;
import io.camunda.optimize.dto.optimize.query.report.single.configuration.TableColumnDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.InputVariableEntry;
import io.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.OutputVariableEntry;
import io.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionInstanceDto;
import io.camunda.optimize.service.db.report.ExecutionContext;
import io.camunda.optimize.service.db.report.plan.decision.DecisionExecutionPlan;
import java.util.List;
import java.util.Set;

public interface RawDecisionInstanceDataGroupByNoneExecutionPlanInterpreter
    extends DecisionExecutionPlanInterpreter {

  @Override
  default Set<DecisionExecutionPlan> getSupportedExecutionPlans() {
    return Set.of(DECISION_RAW_DECISION_INSTANCE_DATA_GROUP_BY_NONE);
  }

  @SuppressWarnings(UNCHECKED_CAST)
  default void addNewVariablesAndDtoFieldsToTableColumnConfig(
      final ExecutionContext<DecisionReportDataDto, DecisionExecutionPlan> executionContext,
      final CommandEvaluationResult<Object> result) {
    final List<RawDataDecisionInstanceDto> firstMeasureData =
        (List<RawDataDecisionInstanceDto>) result.getFirstMeasureData();
    final List<String> variableNames =
        firstMeasureData.stream()
            .flatMap(
                rawDataDecisionInstanceDto ->
                    rawDataDecisionInstanceDto.getInputVariables().values().stream())
            .map(this::getPrefixedInputVariableId)
            .collect(toList());
    variableNames.addAll(
        (firstMeasureData)
            .stream()
                .flatMap(
                    rawDataDecisionInstanceDto ->
                        rawDataDecisionInstanceDto.getOutputVariables().values().stream())
                .map(this::getPrefixedOutputVariableId)
                .toList());

    final TableColumnDto tableColumns =
        executionContext.getReportData().getConfiguration().getTableColumns();
    tableColumns.addNewAndRemoveUnexpectedVariableColumns(variableNames);
    tableColumns.addDtoColumns(extractAllDecisionInstanceDtoFieldKeys());
  }

  default String getPrefixedInputVariableId(final InputVariableEntry inputVariableEntry) {
    return INPUT_PREFIX + inputVariableEntry.getId();
  }

  default String getPrefixedOutputVariableId(final OutputVariableEntry outputVariableEntry) {
    return OUTPUT_PREFIX + outputVariableEntry.getId();
  }
}
