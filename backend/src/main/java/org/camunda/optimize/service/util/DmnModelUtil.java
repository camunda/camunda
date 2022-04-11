/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.model.dmn.Dmn;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.bpm.model.dmn.instance.Decision;
import org.camunda.bpm.model.dmn.instance.DecisionTable;
import org.camunda.bpm.model.dmn.instance.Input;
import org.camunda.bpm.model.dmn.instance.Output;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameResponseDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DmnModelUtil {

  public static DmnModelInstance parseDmnModel(final String dmn10Xml) {
    try (final ByteArrayInputStream stream = new ByteArrayInputStream(dmn10Xml.getBytes())) {
      return Dmn.readModelFromStream(stream);
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Failed reading model", e);
    }
  }

  public static Optional<String> extractDecisionDefinitionName(final String definitionKey, final String xml) {
    try {
      final DmnModelInstance dmnModelInstance = parseDmnModel(xml);
      final Collection<Decision> decisions = dmnModelInstance.getModelElementsByType(Decision.class);

      return decisions.stream()
        .filter(decision -> decision.getId().equals(definitionKey))
        .map(Decision::getName)
        .findFirst();
    } catch (Exception exc) {
      log.warn("Failed parsing the DMN xml.", exc);
      return Optional.empty();
    }
  }

  public static List<DecisionVariableNameResponseDto> extractInputVariables(final DmnModelInstance model,
                                                                            @NonNull final String decisionKey) {
    return extractVariables(model, decisionKey, DmnModelUtil::extractInputVariablesFromDecision);
  }

  public static List<DecisionVariableNameResponseDto> extractOutputVariables(final DmnModelInstance model,
                                                                             @NonNull final String decisionKey) {
    return extractVariables(model, decisionKey, DmnModelUtil::extractOutputVariablesFromDecision);
  }

  private static List<DecisionVariableNameResponseDto> extractVariables(final DmnModelInstance model,
                                                                        @NonNull final String decisionKey,
                                                                        VariableExtractionFunction extractVariables) {
    return model.getModelElementsByType(Decision.class)
      .stream()
      .filter(decision -> Objects.equals(decision.getId(), decisionKey))
      .findFirst()
      .map(decision -> {
        Collection<DecisionTable> decisionTables = decision.getChildElementsByType(DecisionTable.class);
        if (decisionTables.size() < 1) {
          log.warn("Found decision without tables, which is not supported!");
          return new ArrayList<DecisionVariableNameResponseDto>();
        } else if (decisionTables.size() > 1) {
          log.warn("Found decision with multiple tables. Supported is only one!");
          return new ArrayList<DecisionVariableNameResponseDto>();
        }
        DecisionTable firstDecisionTable = decisionTables.iterator().next();
        return extractVariables.extract(firstDecisionTable, decisionKey);
      })
      .orElse(new ArrayList<>());
  }

  private static List<DecisionVariableNameResponseDto> extractInputVariablesFromDecision(final DecisionTable decision, String xmlDecisionKey) {
    final List<DecisionVariableNameResponseDto> inputVariableList = new ArrayList<>();
    for (Input node : decision.getChildElementsByType(Input.class)) {
      DecisionVariableNameResponseDto variableNameDto = new DecisionVariableNameResponseDto();
      variableNameDto.setId(node.getId());
      variableNameDto.setName(node.getLabel());
      String id = node.getInputExpression().getTypeRef();
      if(id == null) {
        log.warn("Found decision input with id {} without a type on decision with key {}, will default to String", node.getId(), xmlDecisionKey);
        variableNameDto.setType(VariableType.STRING);
      }
      else {
        variableNameDto.setType(VariableType.getTypeForId(id));
      }
      inputVariableList.add(variableNameDto);
    }
    return inputVariableList;
  }

  private static List<DecisionVariableNameResponseDto> extractOutputVariablesFromDecision(final DecisionTable decision, String xmlDecisionKey) {
    final List<DecisionVariableNameResponseDto> outputVariableList = new ArrayList<>();
    for (Output node : decision.getChildElementsByType(Output.class)) {
      DecisionVariableNameResponseDto variableNameDto = new DecisionVariableNameResponseDto();
      variableNameDto.setId(node.getId());
      variableNameDto.setName(node.getLabel());
      String id = node.getTypeRef();
      if(id == null) {
        log.warn("Found decision output with id {} without a type on decision with key {}, will default to String", node.getId(), xmlDecisionKey);
        variableNameDto.setType(VariableType.STRING);
      }
      else {
        variableNameDto.setType(VariableType.getTypeForId(id));
      }
      outputVariableList.add(variableNameDto);
    }
    return outputVariableList;
  }

  private interface VariableExtractionFunction {
    List<DecisionVariableNameResponseDto> extract(DecisionTable table, String key);
  }
}

