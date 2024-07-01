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

public class ElementTreePathBuilder {
  private final ElementInstanceState elementInstanceState;
  private final Long elementInstanceKey;
  private final ElementTreePathProperties properties;

  public ElementTreePathBuilder(
      final ElementInstanceState elementInstanceState, final Long elementInstanceKey) {
    this.elementInstanceState = elementInstanceState;
    this.elementInstanceKey = elementInstanceKey;
    properties =
        new ElementTreePathProperties(new LinkedList<>(), new LinkedList<>(), new LinkedList<>());
  }

  public ElementTreePathProperties getProperties() {
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

    final long callingElementInstanceKey = instance.getValue().getParentElementInstanceKey();
    if (callingElementInstanceKey != -1) {
      buildElementTreePathProperties(callingElementInstanceKey);
    }
  }

  public record ElementTreePathProperties(
      Deque<Deque<Long>> elementInstancePath,
      Deque<Long> processDefinitionPath,
      Deque<Long> callingElementPath) {}
}
