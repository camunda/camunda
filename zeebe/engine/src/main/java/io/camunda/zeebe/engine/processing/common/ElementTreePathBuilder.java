/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.common;

import io.camunda.zeebe.engine.state.instance.ElementInstance;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public class ElementTreePathBuilder {

  private ElementInstanceProvider elementInstanceProvider;
  private CallActivityIndexProvider callActivityIndexProvider;
  private Long elementInstanceKey;
  private ElementTreePathProperties properties;

  public ElementTreePathBuilder withElementInstanceProvider(
      final ElementInstanceProvider elementInstanceState) {
    elementInstanceProvider = elementInstanceState;
    return this;
  }

  public ElementTreePathBuilder withCallActivityIndexProvider(
      final CallActivityIndexProvider callActivityIndexProvider) {
    this.callActivityIndexProvider = callActivityIndexProvider;
    return this;
  }

  public ElementTreePathBuilder withElementInstanceKey(final long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
    return this;
  }

  public ElementTreePathProperties build() {
    Objects.requireNonNull(elementInstanceProvider, "elementInstanceProvider cannot be null");
    Objects.requireNonNull(
        callActivityIndexProvider, "call activity index provider cannot be null");
    Objects.requireNonNull(elementInstanceKey, "elementInstanceKey cannot be null");
    properties =
        new ElementTreePathProperties(new LinkedList<>(), new LinkedList<>(), new LinkedList<>());
    buildElementTreePathProperties(elementInstanceKey);
    return properties;
  }

  private void buildElementTreePathProperties(final long elementInstanceKey) {
    final List<Long> elementInstancePath = new LinkedList<>();
    elementInstancePath.add(elementInstanceKey);
    ElementInstance instance = elementInstanceProvider.getInstance(elementInstanceKey);
    if (instance == null) {
      throw new IllegalStateException(
          String.format(
              "Expected to find element instance for given key '%d', but didn't exist.",
              elementInstanceKey));
    }

    long parentElementInstanceKey = instance.getParentKey();
    while (parentElementInstanceKey != -1) {
      instance = elementInstanceProvider.getInstance(parentElementInstanceKey);
      elementInstancePath.addFirst(parentElementInstanceKey);
      parentElementInstanceKey = instance.getParentKey();
    }
    properties.elementInstancePath.addFirst(elementInstancePath);
    final var processInstanceRecord = instance.getValue();
    properties.processDefinitionPath.addFirst(processInstanceRecord.getProcessDefinitionKey());

    final long callingElementInstanceKey = processInstanceRecord.getParentElementInstanceKey();
    if (callingElementInstanceKey != -1) {
      properties.callingElementPath.addFirst(getCallActivityIndex(callingElementInstanceKey));
      buildElementTreePathProperties(callingElementInstanceKey);
    }
  }

  private Integer getCallActivityIndex(final long callingElementInstanceKey) {
    final ElementInstance callActivityElementInstance =
        elementInstanceProvider.getInstance(callingElementInstanceKey);
    final var callActivityInstanceRecord = callActivityElementInstance.getValue();

    return callActivityIndexProvider.getLexicographicIndex(
        callActivityInstanceRecord.getProcessDefinitionKey(),
        callActivityInstanceRecord.getTenantId(),
        callActivityInstanceRecord.getElementIdBuffer());
  }

  public record ElementTreePathProperties(
      List<List<Long>> elementInstancePath,
      List<Long> processDefinitionPath,
      List<Integer> callingElementPath) {}
}
