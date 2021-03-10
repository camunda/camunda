/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.state.appliers;

import io.zeebe.engine.processing.deployment.model.element.ExecutableFlowElementContainer;
import io.zeebe.engine.state.TypedEventApplier;
import io.zeebe.engine.state.immutable.WorkflowState;
import io.zeebe.engine.state.instance.StoredRecord.Purpose;
import io.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.zeebe.engine.state.mutable.MutableEventScopeInstanceState;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;

/** Applies state changes for `WorkflowInstance:Element_Activated` */
final class WorkflowInstanceElementActivatedApplier
    implements TypedEventApplier<WorkflowInstanceIntent, WorkflowInstanceRecord> {

  private final MutableElementInstanceState elementInstanceState;
  private final MutableEventScopeInstanceState eventScopeInstanceState;

  private final WorkflowState workflowState;

  public WorkflowInstanceElementActivatedApplier(
      final MutableElementInstanceState elementInstanceState,
      final WorkflowState workflowState,
      final MutableEventScopeInstanceState eventScopeInstanceState) {
    this.elementInstanceState = elementInstanceState;
    this.workflowState = workflowState;
    this.eventScopeInstanceState = eventScopeInstanceState;
  }

  @Override
  public void applyState(final long key, final WorkflowInstanceRecord value) {
    elementInstanceState.updateInstance(
        key, instance -> instance.setState(WorkflowInstanceIntent.ELEMENT_ACTIVATED));

    // We store the record to use it on resolving the incident, which is no longer used after
    // migrating the incident processor.
    // In order to migrate the other processors we need to write (and here remove) the record in an
    // event applier.
    // todo: we need to remove it later
    elementInstanceState.removeStoredRecord(value.getFlowScopeKey(), key, Purpose.FAILED);
    if (value.getBpmnElementType() == BpmnElementType.SUB_PROCESS) {

      final var executableFlowElementContainer =
          workflowState.getFlowElement(
              value.getWorkflowKey(),
              value.getElementIdBuffer(),
              ExecutableFlowElementContainer.class);

      final var events = executableFlowElementContainer.getEvents();
      if (!events.isEmpty()) {
        eventScopeInstanceState.createIfNotExists(
            key, executableFlowElementContainer.getInterruptingElementIds());
      }
    }
  }
}
