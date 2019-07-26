/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.engine.importing;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.model.dmn.Dmn;
import org.camunda.bpm.model.dmn.DmnModelInstance;
import org.camunda.bpm.model.dmn.instance.Decision;
import org.camunda.bpm.model.dmn.instance.Input;
import org.camunda.bpm.model.dmn.instance.Output;
import org.camunda.optimize.dto.optimize.query.variable.DecisionVariableNameDto;
import org.camunda.optimize.dto.optimize.query.variable.VariableType;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Slf4j
@UtilityClass
public class DmnModelUtility {

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
      final Collection<Decision> processes = dmnModelInstance.getModelElementsByType(Decision.class);

      return processes.stream()
        .filter(decision -> decision.getId().equals(definitionKey))
        .map(Decision::getName)
        .findFirst();
    } catch (Exception exc) {
      log.warn("Failed parsing the DMN xml.", exc);
      return Optional.empty();
    }
  }

  public static List<DecisionVariableNameDto> extractInputVariables(final DmnModelInstance model) {
    final List<DecisionVariableNameDto> result = new ArrayList<>();
    for (Input node : model.getModelElementsByType(Input.class)) {
      DecisionVariableNameDto variableNameDto = new DecisionVariableNameDto();
      variableNameDto.setId(node.getId());
      variableNameDto.setName(node.getLabel());
      variableNameDto.setType(VariableType.getTypeForId(node.getInputExpression().getTypeRef()));
      result.add(variableNameDto);
    }
    return result;
  }

  public static List<DecisionVariableNameDto> extractOutputVariables(final DmnModelInstance model) {
    final List<DecisionVariableNameDto> result = new ArrayList<>();
    for (Output node : model.getModelElementsByType(Output.class)) {
      DecisionVariableNameDto variableNameDto = new DecisionVariableNameDto();
      variableNameDto.setId(node.getId());
      variableNameDto.setName(node.getLabel());
      variableNameDto.setType(VariableType.getTypeForId(node.getTypeRef()));
      result.add(variableNameDto);
    }
    return result;
  }

}
