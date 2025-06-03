/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowNode;
import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceModificationRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceModificationIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.util.Collections;
import java.util.Objects;

final class ProcessInstanceModifiedEventApplier
    implements TypedEventApplier<
        ProcessInstanceModificationIntent, ProcessInstanceModificationRecord> {

  private final MutableElementInstanceState elementInstanceState;
  private final MutableProcessState processState;

  public ProcessInstanceModifiedEventApplier(
      final MutableElementInstanceState elementInstanceState,
      final MutableProcessState processState) {
    this.elementInstanceState = elementInstanceState;
    this.processState = processState;
  }

  @Override
  public void applyState(final long key, final ProcessInstanceModificationRecord value) {
    final var processInstance = elementInstanceState.getInstance(value.getProcessInstanceKey());
    if (processInstance != null && value.hasActivateInstructions()) {
      clearInterruptedState(value);
      incrementNumberOfTakenSequenceFlows(value, processInstance);
    }
  }

  private void clearInterruptedState(final ProcessInstanceModificationRecord value) {
    value.getAncestorScopeKeys().stream()
        .map(elementInstanceState::getInstance)
        // These instances can be null when an element in a flow scope gets activated, but the flow
        // scope gets terminated in this same command
        .filter(Objects::nonNull)
        .filter(ElementInstance::isInterrupted)
        .forEach(
            instance ->
                elementInstanceState.updateInstance(
                    instance.getKey(), ElementInstance::clearInterruptedState));
  }

  private void incrementNumberOfTakenSequenceFlows(
      final ProcessInstanceModificationRecord value, final ElementInstance processInstance) {
    final var process =
        processState
            .getProcessByKeyAndTenant(
                processInstance.getValue().getProcessDefinitionKey(),
                processInstance.getValue().getTenantId())
            .getProcess();
    value
        .getActivateInstructions()
        .forEach(
            instruction -> {
              final var element =
                  process.getElementById(instruction.getElementId(), ExecutableFlowNode.class);
              if (!element.getElementType().equals(BpmnElementType.PARALLEL_GATEWAY)
                  && !element.getElementType().equals(BpmnElementType.INCLUSIVE_GATEWAY)) {
                return;
              }

              // Parent scopes are created in order from outer scope to inner scope. This means that
              // the parent flow scope of the element that must be activated will always be the
              // highest key in the set.
              final var parentFlowScopeKey = Collections.max(instruction.getAncestorScopeKeys());
              element
                  .getIncoming()
                  .forEach(
                      incoming ->
                          elementInstanceState.incrementNumberOfTakenSequenceFlows(
                              parentFlowScopeKey, element.getId(), incoming.getId()));
            });
  }
}
