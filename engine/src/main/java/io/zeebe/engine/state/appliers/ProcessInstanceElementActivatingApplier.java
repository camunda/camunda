/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state.appliers;

import io.zeebe.engine.processing.deployment.model.element.ExecutableStartEvent;
import io.zeebe.engine.state.TypedEventApplier;
import io.zeebe.engine.state.immutable.ProcessState;
import io.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.zeebe.engine.state.mutable.MutableVariableState;
import io.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;

/** Applies state changes for `ProcessInstance:Element_Activating` */
final class ProcessInstanceElementActivatingApplier
    implements TypedEventApplier<ProcessInstanceIntent, ProcessInstanceRecord> {

  private final MutableElementInstanceState elementInstanceState;
  private final MutableVariableState variableState;
  private final ProcessState processState;

  public ProcessInstanceElementActivatingApplier(
      final MutableElementInstanceState elementInstanceState,
      final ProcessState processState,
      final MutableVariableState variableState) {
    this.elementInstanceState = elementInstanceState;
    this.processState = processState;
    this.variableState = variableState;
  }

  @Override
  public void applyState(final long elementInstanceKey, final ProcessInstanceRecord value) {
    final var flowScopeInstance = elementInstanceState.getInstance(value.getFlowScopeKey());
    elementInstanceState.newInstance(
        flowScopeInstance, elementInstanceKey, value, ProcessInstanceIntent.ELEMENT_ACTIVATING);

    if (flowScopeInstance == null) {
      // process instance level
      return;
    }

    final var flowScopeElementType = flowScopeInstance.getValue().getBpmnElementType();
    final var currentElementType = value.getBpmnElementType();

    if (isStartEventInSubProcess(flowScopeElementType, currentElementType)) {

      final var executableStartEvent =
          processState.getFlowElement(
              value.getProcessDefinitionKey(),
              value.getElementIdBuffer(),
              ExecutableStartEvent.class);
      if (!executableStartEvent.isNone()) {
        // IF the current element is a start event and the flow scope is a sub process
        // then it is either a none start event, which means it is a normal *embedded sub process*
        // or an timer/message start event, which means it is a *event sub process*
        // *Only for event sub processes we transfer variables*

        // the event variables are stored as temporary variables in the scope of the
        // subprocess
        // - move them to the scope of the start event to apply the output variable mappings
        final var variables = variableState.getTemporaryVariables(flowScopeInstance.getKey());

        if (variables != null) {
          variableState.setTemporaryVariables(elementInstanceKey, variables);
          variableState.removeTemporaryVariables(flowScopeInstance.getKey());
        }
      }
    }
  }

  private boolean isStartEventInSubProcess(
      final BpmnElementType flowScopeElementType, final BpmnElementType currentElementType) {
    return currentElementType == BpmnElementType.START_EVENT
        && flowScopeElementType == BpmnElementType.SUB_PROCESS;
  }
}
