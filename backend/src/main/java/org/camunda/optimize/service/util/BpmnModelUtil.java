/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a commercial license.
 * You may not use this file except in compliance with the commercial license.
 */
package org.camunda.optimize.service.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.camunda.bpm.model.bpmn.Bpmn;
import org.camunda.bpm.model.bpmn.BpmnModelInstance;
import org.camunda.bpm.model.bpmn.instance.EndEvent;
import org.camunda.bpm.model.bpmn.instance.Event;
import org.camunda.bpm.model.bpmn.instance.FlowElement;
import org.camunda.bpm.model.bpmn.instance.FlowNode;
import org.camunda.bpm.model.bpmn.instance.Process;
import org.camunda.bpm.model.bpmn.instance.StartEvent;
import org.camunda.bpm.model.bpmn.instance.SubProcess;
import org.camunda.bpm.model.bpmn.instance.UserTask;
import org.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.camunda.optimize.service.util.EventDtoBuilderUtil.createCamundaEventTypeDto;

@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class BpmnModelUtil {

  public static BpmnModelInstance parseBpmnModel(final String bpmn20Xml) {
    try (final ByteArrayInputStream stream = new ByteArrayInputStream(bpmn20Xml.getBytes())) {
      return Bpmn.readModelFromStream(stream);
    } catch (IOException e) {
      throw new OptimizeRuntimeException("Failed reading model", e);
    }
  }

  public static Map<String, String> extractFlowNodeNames(final String bpmn20Xml) {
    return extractFlowNodeNames(parseBpmnModel(bpmn20Xml));
  }

  public static Map<String, String> extractFlowNodeNames(final BpmnModelInstance model) {
    return extractFlowNodeNames(model, FlowNode.class);
  }

  public static Map<String, String> extractUserTaskNames(final BpmnModelInstance model) {
    return extractFlowNodeNames(model, UserTask.class);
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

  public static List<EventTypeDto> getStartEventsFromInstance(final BpmnModelInstance modelInstance,
                                                              final String definitionKey) {
    return getEventsFromInstance(modelInstance, StartEvent.class, definitionKey);
  }

  public static List<EventTypeDto> getEndEventsFromInstance(final BpmnModelInstance modelInstance,
                                                            final String definitionKey) {
    return getEventsFromInstance(modelInstance, EndEvent.class, definitionKey);
  }

  private static List<EventTypeDto> getEventsFromInstance(final BpmnModelInstance modelInstance,
                                                          final Class<? extends Event> eventClass,
                                                          final String definitionKey) {
    final List<FlowElement> subProcessStartEndEvents =
      new ArrayList<>(modelInstance.getModelElementsByType(SubProcess.class))
        .stream()
        .filter(Objects::nonNull)
        .flatMap(subProcess -> subProcess.getFlowElements().stream())
        .filter(element -> StartEvent.class.isAssignableFrom(element.getClass()) ||
          EndEvent.class.isAssignableFrom(element.getClass()))
        .collect(Collectors.toList());

    return modelInstance.getModelElementsByType(eventClass)
      .stream()
      .filter(event -> !subProcessStartEndEvents.contains(event))
      .map(event -> {
        final String elementId = event.getAttributeValue("id");
        final String elementName = Optional.ofNullable(event.getAttributeValue("name")).orElse(elementId);
        return createCamundaEventTypeDto(
          definitionKey,
          elementId,
          elementName
        );
      })
      .collect(Collectors.toList());
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
