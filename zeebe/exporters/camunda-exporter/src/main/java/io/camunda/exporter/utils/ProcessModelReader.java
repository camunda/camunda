/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.exporter.utils;

import io.camunda.zeebe.model.bpmn.instance.ExtensionElements;
import io.camunda.zeebe.model.bpmn.instance.Process;
import io.camunda.zeebe.model.bpmn.instance.zeebe.ZeebeUserTaskForm;
import java.util.List;
import java.util.Optional;

public class ProcessModelReader {

  private final Process process;

  public ProcessModelReader(final Process process) {
    this.process = process;
  }

  public Optional<List<EmbeddedForm>> extractEmbeddedForms() {
    return getUserTaskForms(process).map(this::mapToEmbeddedForms);
  }

  private List<EmbeddedForm> mapToEmbeddedForms(final List<ZeebeUserTaskForm> forms) {
    return forms.stream().map(f -> new EmbeddedForm(f.getId(), f.getTextContent())).toList();
  }

  private Optional<List<ZeebeUserTaskForm>> getUserTaskForms(final Process process) {
    return getExtensionElementsFromProcess(process)
        .map(ExtensionElements::getElementsQuery)
        .map(q -> q.filterByType(ZeebeUserTaskForm.class))
        .map(l -> l.count() > 0 ? l.list() : null);
  }

  private Optional<ExtensionElements> getExtensionElementsFromProcess(final Process process) {
    return Optional.ofNullable(process.getExtensionElements());
  }

  public record EmbeddedForm(String id, String schema) {}
}
