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
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.Optional;

@Slf4j
@UtilityClass
public class DmnModelUtility {

  public static DmnModelInstance parseBpmnModel(final String dmn10Xml) {
    try (final ByteArrayInputStream stream = new ByteArrayInputStream(dmn10Xml.getBytes())) {
      return Dmn.readModelFromStream(stream);
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Failed reading model", e);
    }
  }

  public static Optional<String> extractDecisionDefinitionName(final String definitionKey, final String xml) {
    try {
      final DmnModelInstance dmnModelInstante = parseBpmnModel(xml);
      final Collection<Decision> processes = dmnModelInstante.getModelElementsByType(Decision.class);

      return processes.stream()
        .filter(decision -> decision.getId().equals(definitionKey))
        .map(Decision::getName)
        .findFirst();
    } catch (Exception exc) {
      log.warn("Failed parsing the BPMN xml.", exc);
      return Optional.empty();
    }
  }

}
