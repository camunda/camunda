/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.instance.EventTrigger;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableEventScopeInstanceState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;

/** Applies state changes for `ProcessInstance:Element_Terminating` */
final class ProcessInstanceElementTerminatingApplier
    implements TypedEventApplier<ProcessInstanceIntent, ProcessInstanceRecord> {

  private final MutableElementInstanceState elementInstanceState;
  private final MutableEventScopeInstanceState eventScopeInstanceState;

  public ProcessInstanceElementTerminatingApplier(
      final MutableElementInstanceState elementInstanceState,
      final MutableEventScopeInstanceState eventScopeInstanceState) {
    this.elementInstanceState = elementInstanceState;
    this.eventScopeInstanceState = eventScopeInstanceState;
  }

  @Override
  public void applyState(final long key, final ProcessInstanceRecord value) {

    deleteEventTrigger(value);

    elementInstanceState.updateInstance(
        key, instance -> instance.setState(ProcessInstanceIntent.ELEMENT_TERMINATING));
  }

  private void deleteEventTrigger(final ProcessInstanceRecord value) {
    // Event sub processes can still have an event trigger at this point, as this didn't get deleted
    // in order to preserve the variables. If we do not delete this trigger the termination of the
    // process would result in the event sub process being activated a second time.
    if (value.getBpmnElementType() == BpmnElementType.EVENT_SUB_PROCESS) {
      final EventTrigger eventTrigger =
          eventScopeInstanceState.peekEventTrigger(value.getFlowScopeKey());
      if (eventTrigger != null) {
        eventScopeInstanceState.deleteTrigger(value.getFlowScopeKey(), eventTrigger.getEventKey());
      }
    }
  }
}
