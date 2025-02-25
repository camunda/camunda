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
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import io.camunda.zeebe.model.bpmn.instance.BaseElement;
import io.camunda.zeebe.model.bpmn.instance.FlowNode;
import io.camunda.zeebe.model.bpmn.instance.Process;
import io.camunda.zeebe.model.bpmn.instance.SubProcess;
import io.camunda.zeebe.model.bpmn.instance.UserTask;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;

public final class BpmnModelUtil {

  public static final String BPMN_ELEMENT_ATTRIBUTE = "bpmnElement";
  public static final String IS_EXPANDED_ATTRIBUTE = "isExpanded";
  private static final Logger LOG = org.slf4j.LoggerFactory.getLogger(BpmnModelUtil.class);

  private BpmnModelUtil() {}

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
      LOG.warn("Failed parsing the BPMN xml.", exc);
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

  public static Map<String, String> extractFlowNodeNames(final List<FlowNodeDataDto> flowNodeData) {
    final Map<String, String> flowNodeNames = new HashMap<>();
    for (final FlowNodeDataDto flowNode : flowNodeData) {
      flowNodeNames.put(flowNode.getId(), flowNode.getName());
    }
    return flowNodeNames;
  }

  public static Set<String> getCollapsedSubprocessElementIds(final String xmlString) {
    final BpmnModelInstance bpmnModelInstance = parseBpmnModel(xmlString);
    final Map<String, Set<String>> flowNodeIdsBySubprocessId =
        bpmnModelInstance.getModelElementsByType(SubProcess.class).stream()
            .collect(
                Collectors.toMap(
                    BaseElement::getId,
                    subProcess ->
                        subProcess.getFlowElements().stream()
                            .map(BaseElement::getId)
                            .collect(Collectors.toSet())));
    return bpmnModelInstance.getDefinitions().getBpmDiagrams().stream()
        .flatMap(
            diagram ->
                diagram.getBpmnPlane().getDiagramElements().stream()
                    .filter(
                        element ->
                            flowNodeIdsBySubprocessId.containsKey(
                                element.getAttributeValue(BPMN_ELEMENT_ATTRIBUTE))))
        .filter(
            subProcessElement ->
                !Boolean.parseBoolean(subProcessElement.getAttributeValue(IS_EXPANDED_ATTRIBUTE)))
        .flatMap(
            collapsedSubProcess ->
                flowNodeIdsBySubprocessId
                    .getOrDefault(
                        collapsedSubProcess.getAttributeValue(BPMN_ELEMENT_ATTRIBUTE), Set.of())
                    .stream())
        .collect(Collectors.toSet());
  }

  public static String getResourceFileAsString(final String fileName) throws IOException {
    try (final InputStream is = BpmnModelUtil.class.getResourceAsStream(fileName)) {
      try (final InputStreamReader isr = new InputStreamReader(is, StandardCharsets.UTF_8);
          final BufferedReader reader = new BufferedReader(isr)) {
        return reader.lines().collect(Collectors.joining());
      }
    }
  }
}
