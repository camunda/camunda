/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.es.report.command.decision.mapping;

import static java.util.stream.Collectors.toMap;

import io.camunda.optimize.dto.optimize.importing.DecisionInstanceDto;
import io.camunda.optimize.dto.optimize.importing.InputInstanceDto;
import io.camunda.optimize.dto.optimize.importing.OutputInstanceDto;
import io.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.InputVariableEntry;
import io.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.OutputVariableEntry;
import io.camunda.optimize.dto.optimize.query.report.single.decision.result.raw.RawDataDecisionInstanceDto;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;

public class RawDecisionDataResultDtoMapper {

  private static final List<Object> DEFAULT_OUTPUT_VARIABLE_VALUE = Collections.emptyList();
  private static final String DEFAULT_INPUT_VARIABLE_VALUE = "";
  private static final Logger log =
      org.slf4j.LoggerFactory.getLogger(RawDecisionDataResultDtoMapper.class);

  public List<RawDataDecisionInstanceDto> mapFrom(
      final List<DecisionInstanceDto> decisionInstanceDtos,
      final Set<InputVariableEntry> allInputVariables,
      final Set<OutputVariableEntry> allOutputVariables) {
    final List<RawDataDecisionInstanceDto> rawData = new ArrayList<>();
    decisionInstanceDtos.forEach(
        decisionInstanceDto -> {
          allInputVariables.addAll(getInputVariables(decisionInstanceDto));
          allOutputVariables.addAll(getOutputVariables(decisionInstanceDto));

          final RawDataDecisionInstanceDto dataEntry = convertToRawDataEntry(decisionInstanceDto);
          rawData.add(dataEntry);
        });

    ensureEveryRawDataInstanceContainsAllVariables(rawData, allInputVariables, allOutputVariables);

    return rawData;
  }

  private void ensureEveryRawDataInstanceContainsAllVariables(
      final List<RawDataDecisionInstanceDto> rawData,
      final Set<InputVariableEntry> inputVariables,
      final Set<OutputVariableEntry> outputVariables) {
    rawData.forEach(
        data -> {
          inputVariables.forEach(
              inputVariableEntry ->
                  data.getInputVariables()
                      .putIfAbsent(
                          inputVariableEntry.getId(), toDefaultInputVariable(inputVariableEntry)));
          outputVariables.forEach(
              outputVariableEntry ->
                  data.getOutputVariables()
                      .putIfAbsent(
                          outputVariableEntry.getId(),
                          toDefaultOutputVariable(outputVariableEntry)));
        });
  }

  private InputVariableEntry toDefaultInputVariable(final InputVariableEntry inputVariableEntry) {
    inputVariableEntry.setValue(DEFAULT_INPUT_VARIABLE_VALUE);
    return inputVariableEntry;
  }

  private OutputVariableEntry toDefaultOutputVariable(
      final OutputVariableEntry outputVariableEntry) {
    outputVariableEntry.setValues(DEFAULT_OUTPUT_VARIABLE_VALUE);
    return outputVariableEntry;
  }

  private Set<InputVariableEntry> getInputVariables(final DecisionInstanceDto decisionInstanceDto) {
    return decisionInstanceDto.getInputs().stream()
        .map(
            inputInstanceDto ->
                new InputVariableEntry(
                    inputInstanceDto.getClauseId(),
                    inputInstanceDto.getClauseName(),
                    inputInstanceDto.getType(),
                    DEFAULT_INPUT_VARIABLE_VALUE))
        .collect(Collectors.toSet());
  }

  private Set<OutputVariableEntry> getOutputVariables(
      final DecisionInstanceDto decisionInstanceDto) {
    return decisionInstanceDto.getOutputs().stream()
        .map(
            outputInstanceDto ->
                new OutputVariableEntry(
                    outputInstanceDto.getClauseId(),
                    outputInstanceDto.getClauseName(),
                    outputInstanceDto.getType(),
                    DEFAULT_OUTPUT_VARIABLE_VALUE))
        .collect(Collectors.toSet());
  }

  private RawDataDecisionInstanceDto convertToRawDataEntry(
      final DecisionInstanceDto decisionInstanceDto) {
    return new RawDataDecisionInstanceDto(
        decisionInstanceDto.getDecisionDefinitionKey(),
        decisionInstanceDto.getDecisionDefinitionId(),
        decisionInstanceDto.getDecisionInstanceId(),
        decisionInstanceDto.getProcessInstanceId(),
        decisionInstanceDto.getEvaluationDateTime(),
        decisionInstanceDto.getEngine(),
        decisionInstanceDto.getTenantId(),
        decisionInstanceDto.getInputs().stream()
            .collect(toMap(InputInstanceDto::getClauseId, this::mapToVariableEntry)),
        decisionInstanceDto.getOutputs().stream()
            .collect(
                toMap(
                    OutputInstanceDto::getClauseId,
                    this::mapToVariableEntry,
                    (variableEntry, variableEntry2) -> {
                      variableEntry.getValues().addAll(variableEntry2.getValues());
                      return variableEntry;
                    })));
  }

  private InputVariableEntry mapToVariableEntry(final InputInstanceDto inputInstanceDto) {
    return new InputVariableEntry(
        inputInstanceDto.getClauseId(),
        inputInstanceDto.getClauseName(),
        inputInstanceDto.getType(),
        inputInstanceDto.getValue());
  }

  private OutputVariableEntry mapToVariableEntry(final OutputInstanceDto outputInstanceDto) {
    return new OutputVariableEntry(
        outputInstanceDto.getClauseId(),
        outputInstanceDto.getClauseName(),
        outputInstanceDto.getType(),
        outputInstanceDto.getValue());
  }
}
