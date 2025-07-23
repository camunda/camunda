/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.common;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCallActivity;
import io.camunda.zeebe.engine.state.immutable.ElementInstanceState;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceRecordValue;
import java.util.Deque;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public class ElementTreePathBuilder {
  private ElementInstanceState elementInstanceState;
  private ProcessState processState;
  private Long elementInstanceKey;
  private ElementTreePathProperties properties;

  public ElementTreePathBuilder withElementInstanceState(
      final ElementInstanceState elementInstanceState) {
    this.elementInstanceState = elementInstanceState;
    return this;
  }

  public ElementTreePathBuilder withProcessState(final ProcessState processState) {
    this.processState = processState;
    return this;
  }

  public ElementTreePathBuilder withElementInstanceKey(final long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
    return this;
  }

  public ElementTreePathProperties build() {
    Objects.requireNonNull(elementInstanceState, "elementInstanceState cannot be null");
    Objects.requireNonNull(processState, "processState cannot be null");
    Objects.requireNonNull(elementInstanceKey, "elementInstanceKey cannot be null");
    properties =
        new ElementTreePathProperties(new LinkedList<>(), new LinkedList<>(), new LinkedList<>());
    buildElementTreePathProperties(elementInstanceKey);
    return properties;
  }

  private void buildElementTreePathProperties(final long elementInstanceKey) {
    final var elementInstance = getElementInstance(elementInstanceKey);
    final long parentElementInstanceKey = elementInstance.getParentKey();
    final var elementProperties =
        new ElementProperties(
            elementInstanceKey, parentElementInstanceKey, elementInstance.getValue());

    buildElementTreePathProperties(elementProperties);
  }

  private void buildElementTreePathProperties(final ElementProperties elementProperties) {
    final Deque<ElementProperties> elementQueue = new LinkedList<>();
    elementQueue.offer(elementProperties);
    while (!elementQueue.isEmpty()) {
      final var curr = elementQueue.poll();
      var processInstanceRecord = curr.processInstanceRecord;
      // Build current element instance path
      final List<Long> elementInstancePath = new LinkedList<>();
      elementInstancePath.add(curr.elementInstanceKey);
      long currParent = curr.parentElementInstanceKey;
      while (currParent != -1) {
        elementInstancePath.addFirst(currParent);
        final var instance = getElementInstance(currParent);
        processInstanceRecord = instance.getValue();
        currParent = instance.getParentKey();
      }
      properties.elementInstancePath.addFirst(elementInstancePath);
      properties.processDefinitionPath.addFirst(processInstanceRecord.getProcessDefinitionKey());

      // Prepare for call-activity loop: step up if needed
      final long callingElementInstanceKey = processInstanceRecord.getParentElementInstanceKey();
      if (callingElementInstanceKey != -1) {
        properties.callingElementPath.addFirst(getCallActivityIndex(callingElementInstanceKey));
        // For next iteration: walk to the parent process instance
        final ElementInstance callingInstance = getElementInstance(callingElementInstanceKey);
        // Add the calling element instance to the queue
        elementQueue.offer(
            new ElementProperties(
                callingElementInstanceKey,
                callingInstance.getParentKey(),
                callingInstance.getValue()));
      }
    }
  }

  private Integer getCallActivityIndex(final long callingElementInstanceKey) {
    final ElementInstance callActivityElementInstance =
        getElementInstance(callingElementInstanceKey);
    final var callActivityInstanceRecord = callActivityElementInstance.getValue();

    final ExecutableCallActivity callActivity =
        processState.getFlowElement(
            callActivityInstanceRecord.getProcessDefinitionKey(),
            callActivityInstanceRecord.getTenantId(),
            callActivityInstanceRecord.getElementIdBuffer(),
            ExecutableCallActivity.class);

    return callActivity.getLexicographicIndex();
  }

  private ElementInstance getElementInstance(final long elementInstanceKey) {
    final ElementInstance instance = elementInstanceState.getInstance(elementInstanceKey);
    if (instance == null) {
      throw new IllegalStateException(
          String.format(
              "Expected to find element instance for given key '%d', but didn't exist.",
              elementInstanceKey));
    }
    return instance;
  }

  public record ElementTreePathProperties(
      List<List<Long>> elementInstancePath,
      List<Long> processDefinitionPath,
      List<Integer> callingElementPath) {}

  private record ElementProperties(
      Long elementInstanceKey,
      Long parentElementInstanceKey,
      ProcessInstanceRecordValue processInstanceRecord) {}
}
