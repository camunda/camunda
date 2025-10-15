/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.util.modelreader;

import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.Query;
import io.camunda.zeebe.model.bpmn.instance.BaseElement;
import io.camunda.zeebe.model.bpmn.instance.CallActivity;
import io.camunda.zeebe.model.bpmn.instance.ExtensionElements;
import io.camunda.zeebe.model.bpmn.instance.FlowNode;
import io.camunda.zeebe.model.bpmn.instance.Process;
import io.camunda.zeebe.model.bpmn.instance.StartEvent;
import io.camunda.zeebe.model.bpmn.instance.SubProcess;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeFormDefinition;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeProperties;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeUserTaskForm;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProcessModelReader {
  private static final Logger LOGGER = LoggerFactory.getLogger(ProcessModelReader.class);

  private static final String PUBLIC_ACCESS = "publicAccess";

  private final Process process;

  private ProcessModelReader(final Process process) {
    this.process = process;
  }

  public static Optional<ProcessModelReader> of(
      final byte[] byteArray, final String bpmnProcessId) {
    try {
      final var is = new ByteArrayInputStream(byteArray);
      final var bpmnModelInstance = Bpmn.readModelFromStream(is);
      final var processModelInstance = bpmnModelInstance.getModelElementById(bpmnProcessId);
      if (processModelInstance instanceof final Process process) {
        return Optional.of(new ProcessModelReader(process));
      }
    } catch (final Exception e) {
      LOGGER.warn("Unable to parse diagram: " + e.getMessage(), e);
    }
    return Optional.empty();
  }

  public String extractProcessName() {
    return process.getName();
  }

  public Optional<StartFormLink> extractStartFormLink() {
    return getStartEvents(process)
        .map(this::getNoneStartFormDefinition)
        .map(this::createStartFormLink);
  }

  public boolean extractIsPublicAccess() {
    return getStartEvents(process)
        .map(this::getNoneStartZeebeProperties)
        .map(this::isPublic)
        .orElse(false);
  }

  public Collection<FlowNode> extractFlowNodes() {
    final List<FlowNode> flowNodes = new ArrayList<>();
    process.getChildElementsByType(FlowNode.class).forEach(fn -> extractFlowNodes(flowNodes, fn));
    return flowNodes;
  }

  public Collection<CallActivity> extractCallActivities() {
    final List<CallActivity> callActivities = new ArrayList<>();
    process
        .getChildElementsByType(FlowNode.class)
        .forEach(fn -> extractCallActivities(callActivities, fn));
    return callActivities;
  }

  private boolean isPublic(final ZeebeProperties properties) {
    return properties.getProperties().stream()
        .filter(zp -> PUBLIC_ACCESS.equals(zp.getName()))
        .findFirst()
        .map(zp -> Boolean.parseBoolean(zp.getValue()))
        .orElse(false);
  }

  private Optional<Collection<StartEvent>> getStartEvents(final Process process) {
    final var startEvents = process.getChildElementsByType(StartEvent.class);
    if (startEvents != null && !startEvents.isEmpty()) {
      return Optional.of(startEvents);
    } else {
      return Optional.empty();
    }
  }

  private boolean isProcessStartEvent(final StartEvent startEvent) {
    return startEvent.getScope() instanceof Process;
  }

  private boolean isNoneStartEvent(final StartEvent startEvent) {
    return startEvent.getEventDefinitions().isEmpty();
  }

  private boolean hasFormDefinition(final StartEvent startEvent) {
    return queryFormDefinition(startEvent).isPresent();
  }

  private boolean hasZeebeProperties(final StartEvent startEvent) {
    return queryZeebeProperties(startEvent).isPresent();
  }

  private Optional<ZeebeFormDefinition> queryFormDefinition(final StartEvent startEvent) {
    return getExtensionElementQuery(startEvent, ZeebeFormDefinition.class)
        .flatMap(Query::findSingleResult);
  }

  private Optional<ZeebeProperties> queryZeebeProperties(final StartEvent startEvent) {
    return getExtensionElementQuery(startEvent, ZeebeProperties.class)
        .flatMap(Query::findSingleResult);
  }

  private ZeebeFormDefinition getNoneStartFormDefinition(final Collection<StartEvent> startEvents) {
    return startEvents.stream()
        .filter(s -> isProcessStartEvent(s) && isNoneStartEvent(s) && hasFormDefinition(s))
        .findFirst()
        .map(this::getFormDefinition)
        .orElse(null);
  }

  private ZeebeProperties getNoneStartZeebeProperties(final Collection<StartEvent> startEvents) {
    return startEvents.stream()
        .filter(s -> isProcessStartEvent(s) && isNoneStartEvent(s) && hasZeebeProperties(s))
        .findFirst()
        .map(this::getZeebeProperties)
        .orElse(null);
  }

  private ZeebeFormDefinition getFormDefinition(final StartEvent startEvent) {
    return queryFormDefinition(startEvent).orElse(null);
  }

  private ZeebeProperties getZeebeProperties(final StartEvent startEvent) {
    return getExtensionElementQuery(startEvent, ZeebeProperties.class)
        .flatMap(Query::findSingleResult)
        .orElse(null);
  }

  private StartFormLink createStartFormLink(final ZeebeFormDefinition formDefinition) {
    return new StartFormLink(formDefinition.getFormId(), formDefinition.getFormKey());
  }

  public Optional<List<EmbeddedForm>> extractEmbeddedForms() {
    return getUserTaskForms(process).map(this::mapToEmbeddedForms);
  }

  private List<EmbeddedForm> mapToEmbeddedForms(final List<ZeebeUserTaskForm> forms) {
    return forms.stream().map(f -> new EmbeddedForm(f.getId(), f.getTextContent())).toList();
  }

  private Optional<List<ZeebeUserTaskForm>> getUserTaskForms(final Process process) {
    return getExtensionElementQuery(process, ZeebeUserTaskForm.class)
        .map(l -> l.count() > 0 ? l.list() : null);
  }

  private <T extends ModelElementInstance> Optional<Query<T>> getExtensionElementQuery(
      final BaseElement element, final Class<T> cls) {
    return getExtensionElements(element)
        .map(ExtensionElements::getElementsQuery)
        .map(q -> q.filterByType(cls));
  }

  private Optional<ExtensionElements> getExtensionElements(final BaseElement element) {
    return Optional.ofNullable(element.getExtensionElements());
  }

  private void extractFlowNodes(final List<FlowNode> flowNodes, final FlowNode flowNode) {
    flowNodes.add(flowNode);
    flowNode
        .getChildElementsByType(FlowNode.class)
        .forEach(nestedFlowNode -> extractFlowNodes(flowNodes, nestedFlowNode));
  }

  private void extractCallActivities(
      final List<CallActivity> callActivities, final FlowNode flowNode) {
    if (flowNode instanceof CallActivity) {
      callActivities.add((CallActivity) flowNode);
    } else if (flowNode instanceof SubProcess) {
      flowNode
          .getChildElementsByType(FlowNode.class)
          .forEach(fn -> extractCallActivities(callActivities, fn));
    }
  }

  public record EmbeddedForm(String id, String schema) {}

  public record StartFormLink(String formId, String formKey) {}
}
