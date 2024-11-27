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
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

public class ElementTreePathBuilder {
  private ElementInstanceState elementInstanceState;
  private ProcessState processState;
  private Long elementInstanceKey;
  private ElementTreePathProperties properties;
  private long flowScopeKey;
  private ProcessInstanceRecordValue processInstanceRecordValue;

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

  public ElementTreePathBuilder withFlowScopeKey(final long flowScopeKey) {
    this.flowScopeKey = flowScopeKey;
    return this;
  }

  public ElementTreePathBuilder withRecordValue(
      final ProcessInstanceRecordValue processInstanceRecordValue) {
    this.processInstanceRecordValue = processInstanceRecordValue;
    return this;
  }

  public ElementTreePathProperties build() {
    Objects.requireNonNull(elementInstanceState, "elementInstanceState cannot be null");
    Objects.requireNonNull(processState, "processState cannot be null");
    Objects.requireNonNull(elementInstanceKey, "elementInstanceKey cannot be null");
    properties =
        new ElementTreePathProperties(new LinkedList<>(), new LinkedList<>(), new LinkedList<>());

    if (processInstanceRecordValue != null) {
      buildElementTreePathProperties(flowScopeKey, processInstanceRecordValue);
    } else {
      buildElementTreePathProperties(elementInstanceKey);
    }
    return properties;
  }

  private void buildElementTreePathProperties(final long elementInstanceKey) {
    final ElementInstance instance = elementInstanceState.getInstance(elementInstanceKey);
    final long parentElementInstanceKey = instance.getParentKey();
    buildElementTreePathProperties(parentElementInstanceKey, instance.getValue());
  }

  private void buildElementTreePathProperties(
      long parentElementInstanceKey, final ProcessInstanceRecordValue processInstanceRecord) {
    final List<Long> elementInstancePath = new LinkedList<>();
    elementInstancePath.add(elementInstanceKey);
    while (parentElementInstanceKey != -1) {
      final var instance = elementInstanceState.getInstance(parentElementInstanceKey);
      elementInstancePath.addFirst(parentElementInstanceKey);
      parentElementInstanceKey = instance.getParentKey();
    }
    properties.elementInstancePath.addFirst(elementInstancePath);
    properties.processDefinitionPath.addFirst(processInstanceRecord.getProcessDefinitionKey());

    final long callingElementInstanceKey = processInstanceRecord.getParentElementInstanceKey();
    if (callingElementInstanceKey != -1) {
      properties.callingElementPath.addFirst(getCallActivityIndex(callingElementInstanceKey));
      buildElementTreePathProperties(callingElementInstanceKey);
    }
  }

  private Integer getCallActivityIndex(final long callingElementInstanceKey) {
    final ElementInstance callActivityElementInstance =
        elementInstanceState.getInstance(callingElementInstanceKey);
    final var callActivityInstanceRecord = callActivityElementInstance.getValue();

    final ExecutableCallActivity callActivity =
        processState.getFlowElement(
            callActivityInstanceRecord.getProcessDefinitionKey(),
            callActivityInstanceRecord.getTenantId(),
            callActivityInstanceRecord.getElementIdBuffer(),
            ExecutableCallActivity.class);

    return callActivity.getLexicographicIndex();
  }

  public record ElementTreePathProperties(
      List<List<Long>> elementInstancePath,
      List<Long> processDefinitionPath,
      List<Integer> callingElementPath) {}
}
