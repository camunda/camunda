/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.bpmn.instance.UserTask;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Slf4j
@UtilityClass
public class BpmnModelUtility {
  public static Map<String, String> extractFlowNodeNames(final BpmnModelInstance model) {
    return extractFlowNodeNames(model, FlowNode.class);
  }

  public static Map<String, String> extractUserTaskNames(final BpmnModelInstance model) {
    return extractFlowNodeNames(model, UserTask.class);
  }

  public static BpmnModelInstance parseBpmnModel(final String bpmn20Xml) {
    try (final ByteArrayInputStream stream = new ByteArrayInputStream(bpmn20Xml.getBytes())) {
      return Bpmn.readModelFromStream(stream);
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Failed reading model", e);
    }
  }

  public static Optional<String> extractProcessDefinitionName(final String definitionKey, final String xml) {
    try {
      final BpmnModelInstance bpmnModelInstance = parseBpmnModel(xml);
      final Collection<Process> processes = bpmnModelInstance.getModelElementsByType(Process.class);

      return processes.stream()
        .filter(process -> process.getId().equals(definitionKey))
        .map(Process::getName)
        .findFirst();
    } catch (Exception exc) {
      log.warn("Failed parsing the BPMN xml.", exc);
      return Optional.empty();
    }
  }

  private static <T extends FlowNode> Map<String, String> extractFlowNodeNames(final BpmnModelInstance model,
                                                                               final Class<T> nodeType) {
    final Map<String, String> result = new HashMap<>();
    for (T node : model.getModelElementsByType(nodeType)) {
      result.put(node.getId(), node.getName());
    }
    return result;
  }
}
