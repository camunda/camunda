/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state.appliers;

import io.zeebe.engine.state.TypedEventApplier;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.engine.state.instance.StoredRecord.Purpose;
import io.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;

/** Applies state changes for `ProcessInstance:Element_Activating` */
final class ProcessInstanceElementActivatingApplier
    implements TypedEventApplier<ProcessInstanceIntent, ProcessInstanceRecord> {

  private final MutableElementInstanceState elementInstanceState;

  public ProcessInstanceElementActivatingApplier(
      final MutableElementInstanceState elementInstanceState) {
    this.elementInstanceState = elementInstanceState;
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

    if (isContainerElement(flowScopeElementType, currentElementType)
        || isNonInterruptingEventSubprocess(flowScopeInstance, currentElementType)
        || currentElementType == BpmnElementType.BOUNDARY_EVENT
        || currentElementType == BpmnElementType.INTERMEDIATE_CATCH_EVENT) {
      // we currently spawn new tokens only for:
      // container elements, non interrupting event sub process, boundary events and intermediate
      // catch events
      // we might spawn tokens for other bpmn element types as well later, then we can improve
      // here
      elementInstanceState.spawnToken(flowScopeInstance.getKey());
    }

    // We store the record to use it on resolving the incident, which is no longer used after
    // migrating the incident processor.
    // In order to migrate the other processors we need to write the record in an event applier.
    // The
    // record is removed in the ACTIVATED again
    // (which happens either after resolving or immediately)
    // todo: we need to remove it later
    elementInstanceState.storeRecord(
        elementInstanceKey,
        value.getFlowScopeKey(),
        value,
        ProcessInstanceIntent.ACTIVATE_ELEMENT,
        Purpose.FAILED);
  }

  private boolean isNonInterruptingEventSubprocess(
      final ElementInstance flowScopeInstance, final BpmnElementType currentElementType) {
    final var flowScopeElementType = flowScopeInstance.getValue().getBpmnElementType();

    return currentElementType == BpmnElementType.SUB_PROCESS
        && flowScopeElementType == BpmnElementType.PROCESS
        && !isInterrupted(flowScopeInstance);
  }

  private boolean isInterrupted(final ElementInstance elementInstance) {
    return elementInstance.getNumberOfActiveTokens() == 2
        && elementInstance.isInterrupted()
        && elementInstance.isActive();
  }

  private boolean isContainerElement(
      final BpmnElementType flowScopeElementType, final BpmnElementType currentElementType) {
    return (currentElementType == BpmnElementType.START_EVENT
            && (flowScopeElementType == BpmnElementType.SUB_PROCESS
                || flowScopeElementType == BpmnElementType.PROCESS))
        || flowScopeElementType == BpmnElementType.MULTI_INSTANCE_BODY;
  }
}
