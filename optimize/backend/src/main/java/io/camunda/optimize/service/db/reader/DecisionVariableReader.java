/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.db.reader;

import static io.camunda.optimize.service.db.schema.index.DecisionInstanceIndex.INPUTS;
import static io.camunda.optimize.service.db.schema.index.DecisionInstanceIndex.OUTPUTS;

import io.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameResponseDto;
import io.camunda.optimize.dto.optimize.query.variable.DecisionVariableValueRequestDto;
import io.camunda.optimize.service.db.repository.VariableRepository;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.util.Collections;
import java.util.List;
import org.slf4j.Logger;
import org.springframework.stereotype.Component;

@Component
public class DecisionVariableReader {

  private static final Logger log = org.slf4j.LoggerFactory.getLogger(DecisionVariableReader.class);
  private final DecisionDefinitionReader decisionDefinitionReader;
  private final VariableRepository variableRepository;

  public DecisionVariableReader(
      final DecisionDefinitionReader decisionDefinitionReader,
      final VariableRepository variableRepository) {
    this.decisionDefinitionReader = decisionDefinitionReader;
    this.variableRepository = variableRepository;
  }

  public List<DecisionVariableNameResponseDto> getInputVariableNames(
      final String decisionDefinitionKey,
      final List<String> decisionDefinitionVersions,
      final List<String> tenantIds) {
    if (decisionDefinitionVersions == null || decisionDefinitionVersions.isEmpty()) {
      log.debug(
          "Cannot fetch output variable values for decision definition with missing versions.");
      return Collections.emptyList();
    }

    final List<DecisionVariableNameResponseDto> decisionDefinitions =
        decisionDefinitionReader
            .getDecisionDefinition(decisionDefinitionKey, decisionDefinitionVersions, tenantIds)
            .orElseThrow(
                () ->
                    new OptimizeRuntimeException(
                        "Could not extract input variables. Requested decision definition not found!"))
            .getInputVariableNames();

    decisionDefinitions.forEach(
        definition -> {
          if (definition.getName() == null) {
            definition.setName(definition.getId());
          }
        });
    return decisionDefinitions;
  }

  public List<DecisionVariableNameResponseDto> getOutputVariableNames(
      final String decisionDefinitionKey,
      final List<String> decisionDefinitionVersions,
      final List<String> tenantIds) {
    if (decisionDefinitionVersions == null || decisionDefinitionVersions.isEmpty()) {
      return Collections.emptyList();
    } else {
      final List<DecisionVariableNameResponseDto> decisionDefinitions =
          decisionDefinitionReader
              .getDecisionDefinition(decisionDefinitionKey, decisionDefinitionVersions, tenantIds)
              .orElseThrow(
                  () ->
                      new OptimizeRuntimeException(
                          "Could not extract output variables. Requested decision definition not found!"))
              .getOutputVariableNames();

      decisionDefinitions.forEach(
          definition -> {
            if (definition.getName() == null) {
              definition.setName(definition.getId());
            }
          });
      return decisionDefinitions;
    }
  }

  public List<String> getInputVariableValues(final DecisionVariableValueRequestDto requestDto) {
    if (requestDto.getDecisionDefinitionVersions() == null
        || requestDto.getDecisionDefinitionVersions().isEmpty()) {
      log.debug(
          "Cannot fetch input variable values for decision definition with missing versions.");
      return Collections.emptyList();
    }

    log.debug(
        "Fetching input variable values for decision definition with key [{}] and versions [{}]",
        requestDto.getDecisionDefinitionKey(),
        requestDto.getDecisionDefinitionVersions());

    return getVariableValues(requestDto, INPUTS);
  }

  public List<String> getOutputVariableValues(final DecisionVariableValueRequestDto requestDto) {
    if (requestDto.getDecisionDefinitionVersions() == null
        || requestDto.getDecisionDefinitionVersions().isEmpty()) {
      log.debug(
          "Cannot fetch output variable values for decision definition with missing versions.");
      return Collections.emptyList();
    }

    log.debug(
        "Fetching output variable values for decision definition with key [{}] and versions [{}]",
        requestDto.getDecisionDefinitionKey(),
        requestDto.getDecisionDefinitionVersions());

    return getVariableValues(requestDto, OUTPUTS);
  }

  private List<String> getVariableValues(
      final DecisionVariableValueRequestDto requestDto, final String variablesPath) {
    return variableRepository.getDecisionVariableValues(requestDto, variablesPath);
  }
}
