/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.es.report.command.decision.mapping;

import lombok.extern.slf4j.Slf4j;
import org.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import org.camunda.optimize.dto.optimize.importing.InputInstanceDto;
import org.camunda.optimize.dto.optimize.importing.OutputInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.DecisionReportDataDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.InputVariableEntry;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.OutputVariableEntry;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionInstanceDto;
import org.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionReportResultDto;
import org.camunda.optimize.service.es.report.command.exec.ExecutionContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toMap;

@Slf4j
public class RawDecisionDataResultDtoMapper {

  public RawDataDecisionReportResultDto mapFrom(final List<DecisionInstanceDto> decisionInstanceDtos,
                                                final long totalHits,
                                                final ExecutionContext<DecisionReportDataDto> context) {
    final List<RawDataDecisionInstanceDto> rawData = new ArrayList<>();
    final Set<InputVariableEntry> allInputVariablesWithBlankValue = new LinkedHashSet<>();
    final Set<OutputVariableEntry> allOutputVariablesWithNoValues = new LinkedHashSet<>();

    decisionInstanceDtos
      .forEach(decisionInstanceDto -> {
        allInputVariablesWithBlankValue.addAll(getInputVariables(decisionInstanceDto));
        allOutputVariablesWithNoValues.addAll(getOutputVariables(decisionInstanceDto));

        RawDataDecisionInstanceDto dataEntry = convertToRawDataEntry(decisionInstanceDto);
        rawData.add(dataEntry);
      });

    ensureEveryRawDataInstanceContainsAllVariables(
      rawData, allInputVariablesWithBlankValue, allOutputVariablesWithNoValues
    );

    return createResult(rawData, totalHits, context);
  }

  private void ensureEveryRawDataInstanceContainsAllVariables(final List<RawDataDecisionInstanceDto> rawData,
                                                              final Set<InputVariableEntry> inputVariables,
                                                              final Set<OutputVariableEntry> outputVariables) {
    rawData.forEach(data -> {
      inputVariables.forEach(
        inputVariableEntry -> data.getInputVariables().putIfAbsent(inputVariableEntry.getId(), inputVariableEntry)
      );
      outputVariables.forEach(
        outputVariableEntry -> data.getOutputVariables().putIfAbsent(outputVariableEntry.getId(), outputVariableEntry)
      );
    });
  }

  private Set<InputVariableEntry> getInputVariables(final DecisionInstanceDto decisionInstanceDto) {
    return decisionInstanceDto.getInputs().stream()
      .map(inputInstanceDto -> new InputVariableEntry(
        inputInstanceDto.getClauseId(),
        inputInstanceDto.getClauseName(),
        inputInstanceDto.getType(),
        ""
      ))
      .collect(Collectors.toSet());
  }

  private Set<OutputVariableEntry> getOutputVariables(final DecisionInstanceDto decisionInstanceDto) {
    return decisionInstanceDto.getOutputs().stream()
      .map(outputInstanceDto -> new OutputVariableEntry(
        outputInstanceDto.getClauseId(),
        outputInstanceDto.getClauseName(),
        outputInstanceDto.getType(),
        Collections.emptyList()
      ))
      .collect(Collectors.toSet());
  }

  private RawDataDecisionInstanceDto convertToRawDataEntry(final DecisionInstanceDto decisionInstanceDto) {
    return new RawDataDecisionInstanceDto(
      decisionInstanceDto.getDecisionDefinitionKey(),
      decisionInstanceDto.getDecisionDefinitionId(),
      decisionInstanceDto.getDecisionInstanceId(),
      decisionInstanceDto.getProcessInstanceId(),
      decisionInstanceDto.getEvaluationDateTime(),
      decisionInstanceDto.getEngine(),
      decisionInstanceDto.getTenantId(),
      decisionInstanceDto.getInputs().stream().collect(toMap(
        InputInstanceDto::getClauseId,
        this::mapToVariableEntry
      )),

      decisionInstanceDto.getOutputs().stream().collect(toMap(
        OutputInstanceDto::getClauseId,
        this::mapToVariableEntry,
        (variableEntry, variableEntry2) -> {
          variableEntry.getValues().addAll(variableEntry2.getValues());
          return variableEntry;
        }
      ))
    );
  }

  private InputVariableEntry mapToVariableEntry(final InputInstanceDto inputInstanceDto) {
    return new InputVariableEntry(
      inputInstanceDto.getClauseId(),
      inputInstanceDto.getClauseName(),
      inputInstanceDto.getType(),
      inputInstanceDto.getValue()
    );
  }

  private OutputVariableEntry mapToVariableEntry(final OutputInstanceDto outputInstanceDto) {
    return new OutputVariableEntry(
      outputInstanceDto.getClauseId(),
      outputInstanceDto.getClauseName(),
      outputInstanceDto.getType(),
      outputInstanceDto.getValue()
    );
  }

  private RawDataDecisionReportResultDto createResult(final List<RawDataDecisionInstanceDto> limitedRawDataResult,
                                                      final Long totalHits,
                                                      final ExecutionContext<DecisionReportDataDto> context) {
    final RawDataDecisionReportResultDto result = new RawDataDecisionReportResultDto();
    result.addMeasureData(limitedRawDataResult);
    result.setInstanceCount(totalHits);
    result.setInstanceCountWithoutFilters(context.getUnfilteredInstanceCount());
    result.setPagination(context.getPagination());
    return result;
  }

}
