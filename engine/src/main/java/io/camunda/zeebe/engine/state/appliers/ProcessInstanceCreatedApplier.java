/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableProcess;
import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationRecord;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceCreationStartInstruction;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceCreationIntent;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;

public class ProcessInstanceCreatedApplier
    implements TypedEventApplier<ProcessInstanceCreationIntent, ProcessInstanceCreationRecord> {

  private final MutableElementInstanceState elementInstanceState;
  private final ProcessState processState;

  public ProcessInstanceCreatedApplier(
      final MutableElementInstanceState elementInstanceState, final ProcessState processState) {

    this.elementInstanceState = elementInstanceState;
    this.processState = processState;
  }

  @Override
  public void applyState(final long key, final ProcessInstanceCreationRecord value) {
    if (!value.startInstructions().isEmpty()) {
      // we don't need to increment here because the events are already applied
      // incrementActiveSequenceFlowsForScopes(value.getProcessInstanceKey());

      final var process =
          processState.getProcessByKey(value.getProcessDefinitionKey()).getProcess();
      incrementActiveSequenceFlowsForEachStartElement(
          value.getProcessInstanceKey(), value.startInstructions(), process);
    }
  }

  private void incrementActiveSequenceFlowsForScopes(final long key) {
    final ElementInstance elementInstance = elementInstanceState.getInstance(key);
    final List<ElementInstance> childInstances =
        elementInstanceState.getChildren(elementInstance.getKey());

    // TODO childrenInstances is always empty?
    if (!childInstances.isEmpty()) {
      childInstances.forEach(
          child -> {
            incrementActiveSequenceFlows(elementInstance);
            incrementActiveSequenceFlowsForScopes(child.getKey());
          });
    }
  }

  private void incrementActiveSequenceFlowsForEachStartElement(
      final long elementInstanceKey,
      final ArrayProperty<ProcessInstanceCreationStartInstruction> startInstructions,
      final ExecutableProcess process) {

    final var instance = elementInstanceState.getInstance(elementInstanceKey);

    for (final ProcessInstanceCreationStartInstruction startInstruction : startInstructions) {
      final var elementId = startInstruction.getElementId();
      final var element = process.getElementById(BufferUtil.wrapString(elementId));
      final var flowScopeOfElement = element.getFlowScope();

      if (instance.getValue().getElementIdBuffer().equals(flowScopeOfElement.getId())) {
        incrementActiveSequenceFlows(instance);
      }
    }

    final var children = elementInstanceState.getChildren(instance.getKey());
    children.forEach(
        child ->
            incrementActiveSequenceFlowsForEachStartElement(
                child.getKey(), startInstructions, process));
  }

  private void incrementActiveSequenceFlows(final ElementInstance instance) {
    instance.incrementActiveSequenceFlows();
    elementInstanceState.updateInstance(instance);
  }
}
