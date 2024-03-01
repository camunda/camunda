/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under one or more contributor license agreements.
 * Licensed under a proprietary license. See the License.txt file for more information.
 * You may not use this file except in compliance with the proprietary license.
 */
package org.camunda.optimize.service.util;

import static org.camunda.optimize.service.util.EventDtoBuilderUtil.createCamundaEventTypeDto;

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
import org.camunda.optimize.dto.optimize.FlowNodeDataDto;
import org.camunda.optimize.dto.optimize.query.event.process.EventTypeDto;
import org.camunda.optimize.service.exceptions.OptimizeRuntimeException;

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

  public static List<EventTypeDto> getStartEventsFromInstance(
      final BpmnModelInstance modelInstance, final String definitionKey) {
    return getEventsFromInstance(modelInstance, StartEvent.class, definitionKey);
  }

  public static List<EventTypeDto> getEndEventsFromInstance(
      final BpmnModelInstance modelInstance, final String definitionKey) {
    return getEventsFromInstance(modelInstance, EndEvent.class, definitionKey);
  }

  private static List<EventTypeDto> getEventsFromInstance(
      final BpmnModelInstance modelInstance,
      final Class<? extends Event> eventClass,
      final String definitionKey) {
    final List<FlowElement> subProcessStartEndEvents =
        new ArrayList<>(modelInstance.getModelElementsByType(SubProcess.class))
            .stream()
                .filter(Objects::nonNull)
                .flatMap(subProcess -> subProcess.getFlowElements().stream())
                .filter(
                    element ->
                        StartEvent.class.isAssignableFrom(element.getClass())
                            || EndEvent.class.isAssignableFrom(element.getClass()))
                .toList();

    return modelInstance.getModelElementsByType(eventClass).stream()
        .filter(event -> !subProcessStartEndEvents.contains(event))
        .map(
            event -> {
              final String elementId = event.getAttributeValue("id");
              final String elementName =
                  Optional.ofNullable(event.getAttributeValue("name")).orElse(elementId);
              return createCamundaEventTypeDto(definitionKey, elementId, elementName);
            })
        .collect(Collectors.toList());
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
