/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.common;

import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Objects;

public class ElementTreePathBuilder {
  private ElementInstanceState elementInstanceState;
  private Long elementInstanceKey;
  private ElementTreePathProperties properties;

  public ElementTreePathBuilder() {}

  public ElementTreePathBuilder withElementInstanceState(
      final ElementInstanceState elementInstanceState) {
    this.elementInstanceState = elementInstanceState;
    return this;
  }

  public ElementTreePathBuilder withElementInstanceKey(final long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
    return this;
  }

  public ElementTreePathProperties build() {
    Objects.requireNonNull(elementInstanceState, "elementInstanceState cannot be null");
    Objects.requireNonNull(elementInstanceKey, "elementInstanceKey cannot be null");
    properties =
        new ElementTreePathProperties(new LinkedList<>(), new LinkedList<>(), new LinkedList<>());
    buildElementTreePathProperties(elementInstanceKey);
    return properties;
  }

  private void buildElementTreePathProperties(final long elementInstanceKey) {
    final Deque<Long> elementInstancePath = new LinkedList<>();
    elementInstancePath.add(elementInstanceKey);
    ElementInstance instance = elementInstanceState.getInstance(elementInstanceKey);
    long parentElementInstanceKey = instance.getParentKey();
    while (parentElementInstanceKey != -1) {
      instance = elementInstanceState.getInstance(parentElementInstanceKey);
      elementInstancePath.addFirst(parentElementInstanceKey);
      parentElementInstanceKey = instance.getParentKey();
    }
    properties.elementInstancePath.addFirst(elementInstancePath);
    final var processInstanceRecord = instance.getValue();
    properties.processDefinitionPath.addFirst(processInstanceRecord.getProcessDefinitionKey());

    final long callingElementInstanceKey = processInstanceRecord.getParentElementInstanceKey();
    if (callingElementInstanceKey != -1) {
      buildElementTreePathProperties(callingElementInstanceKey);
    }
  }

  public record ElementTreePathProperties(
      Deque<Deque<Long>> elementInstancePath,
      Deque<Long> processDefinitionPath,
      Deque<Long> callingElementPath) {}
}
