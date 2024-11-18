/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.utils;

import io.camunda.zeebe.model.bpmn.Query;
import io.camunda.zeebe.model.bpmn.instance.BaseElement;
import io.camunda.zeebe.model.bpmn.instance.ExtensionElements;
import io.camunda.zeebe.model.bpmn.instance.Process;
import io.camunda.zeebe.model.bpmn.instance.StartEvent;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeFormDefinition;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeUserTaskForm;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;

public class ProcessModelReader {

  private final Process process;

  public ProcessModelReader(final Process process) {
    this.process = process;
  }

  public Optional<StartFormLink> extractStartFormLink() {
    return getStartEvents(process)
        .map(this::getNoneStartFormDefinition)
        .map(this::createStartFormLink);
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

  private Optional<ZeebeFormDefinition> queryFormDefinition(final StartEvent startEvent) {
    return getExtensionElementQuery(startEvent, ZeebeFormDefinition.class)
        .flatMap(Query::findSingleResult);
  }

  private ZeebeFormDefinition getNoneStartFormDefinition(final Collection<StartEvent> startEvents) {
    return startEvents.stream()
        .filter(s -> isProcessStartEvent(s) && isNoneStartEvent(s) && hasFormDefinition(s))
        .findFirst()
        .map(this::getFormDefinition)
        .orElse(null);
  }

  private ZeebeFormDefinition getFormDefinition(final StartEvent startEvent) {
    return queryFormDefinition(startEvent).orElse(null);
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

  public record EmbeddedForm(String id, String schema) {}

  public record StartFormLink(String formId, String formKey) {}
}
