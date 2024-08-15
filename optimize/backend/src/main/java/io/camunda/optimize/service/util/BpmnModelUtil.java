/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.optimize.service.util;

import io.camunda.optimize.dto.optimize.FlowNodeDataDto;
import io.camunda.optimize.service.exceptions.OptimizeRuntimeException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.bpmn.instance.UserTask;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class BpmnModelUtil {

  public static BpmnModelInstance parseBpmnModel(final String bpmn20Xml) {
    try (final ByteArrayInputStream stream = new ByteArrayInputStream(bpmn20Xml.getBytes())) {
      return Bpmn.readModelFromStream(stream);
    } catch (final IOException e) {
      throw new OptimizeRuntimeException("Failed reading model", e);
    }
  }

  public static List<FlowNodeDataDto> extractFlowNodeData(final String bpmn20Xml) {
    return extractFlowNodeData(parseBpmnModel(bpmn20Xml));
  }

  public static Map<String, String> extractUserTaskNames(final String bpmn20Xml) {
    return extractUserTaskNames(parseBpmnModel(bpmn20Xml));
  }

  public static Optional<String> extractProcessDefinitionName(
      final String definitionKey, final String xml) {
    try {
      final BpmnModelInstance bpmnModelInstance = parseBpmnModel(xml);
      final Collection<Process> processes = bpmnModelInstance.getModelElementsByType(Process.class);

      return processes.stream()
          .filter(process -> process.getId().equals(definitionKey))
          .map(Process::getName)
          .filter(Objects::nonNull)
          .findFirst();
    } catch (final Exception exc) {
      log.warn("Failed parsing the BPMN xml.", exc);
      return Optional.empty();
    }
  }

  public static Map<String, String> extractUserTaskNames(final BpmnModelInstance model) {
    final Map<String, String> result = new HashMap<>();
    for (final UserTask userTask : model.getModelElementsByType(UserTask.class)) {
      result.put(userTask.getId(), userTask.getName());
    }
    return result;
  }

  public static List<FlowNodeDataDto> extractFlowNodeData(final BpmnModelInstance model) {
    final List<FlowNodeDataDto> result = new ArrayList<>();
    for (final FlowNode node : model.getModelElementsByType(FlowNode.class)) {
      final FlowNodeDataDto flowNode =
          new FlowNodeDataDto(node.getId(), node.getName(), node.getElementType().getTypeName());
      result.add(flowNode);
    }
    return result;
  }

  public static Map<String, String> extractFlowNodeNames(final List<FlowNodeDataDto> flowNodes) {
    final Map<String, String> flowNodeNames = new HashMap<>();
    for (final FlowNodeDataDto flowNode : flowNodes) {
      flowNodeNames.put(flowNode.getId(), flowNode.getName());
    }
    return flowNodeNames;
  }
}
