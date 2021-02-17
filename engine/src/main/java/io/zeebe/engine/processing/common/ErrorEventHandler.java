/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.common;

import io.zeebe.engine.processing.deployment.model.element.ExecutableActivity;
import io.zeebe.engine.processing.deployment.model.element.ExecutableCatchEvent;
import io.zeebe.engine.processing.deployment.model.element.ExecutableWorkflow;
import io.zeebe.engine.processing.streamprocessor.writers.TypedEventWriter;
import io.zeebe.engine.state.KeyGenerator;
import io.zeebe.engine.state.immutable.ElementInstanceState;
import io.zeebe.engine.state.immutable.WorkflowState;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.engine.state.mutable.MutableEventScopeInstanceState;
import org.agrona.DirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class ErrorEventHandler {

  private static final DirectBuffer NO_VARIABLES = new UnsafeBuffer();

  private final CatchEventTuple catchEventTuple = new CatchEventTuple();

  private final WorkflowState workflowState;
  private final ElementInstanceState elementInstanceState;
  private final EventHandle eventHandle;

  public ErrorEventHandler(
      final WorkflowState workflowState,
      final ElementInstanceState elementInstanceState,
      final MutableEventScopeInstanceState eventScopeInstanceState,
      final KeyGenerator keyGenerator) {
    this.workflowState = workflowState;
    this.elementInstanceState = elementInstanceState;

    eventHandle = new EventHandle(keyGenerator, eventScopeInstanceState);
  }

  /**
   * Throw an error event. The event is propagated from the given instance through the scope
   * hierarchy until the event is caught by a catch event. The event is only thrown if a catch event
   * was found.
   *
   * @param errorCode the error code of the error event
   * @param instance the instance there the event propagation starts
   * @param eventWriter the writer to be used for writing the followup event
   * @return {@code true} if the error event is thrown and caught by an catch event
   */
  public boolean throwErrorEvent(
      final DirectBuffer errorCode,
      final ElementInstance instance,
      final TypedEventWriter eventWriter) {

    final var foundCatchEvent = findCatchEvent(errorCode, instance);
    if (foundCatchEvent != null) {

      eventHandle.triggerEvent(
          eventWriter, foundCatchEvent.instance, foundCatchEvent.catchEvent, NO_VARIABLES);

      return true;
    }

    return false;
  }

  public boolean hasCatchEvent(final DirectBuffer errorCode, final ElementInstance instance) {
    return findCatchEvent(errorCode, instance) != null;
  }

  private CatchEventTuple findCatchEvent(final DirectBuffer errorCode, ElementInstance instance) {
    // assuming that error events are used rarely
    // - just walk through the scope hierarchy and look for a matching catch event

    while (instance != null && instance.isActive()) {
      final var instanceRecord = instance.getValue();
      final var workflow = getWorkflow(instanceRecord.getWorkflowKey());

      final var found = findCatchEventInWorkflow(errorCode, workflow, instance);
      if (found != null) {
        return found;
      }

      // find in parent workflow instance if exists
      final var parentElementInstanceKey = instanceRecord.getParentElementInstanceKey();
      instance = elementInstanceState.getInstance(parentElementInstanceKey);
    }

    // no matching catch event found
    return null;
  }

  private CatchEventTuple findCatchEventInWorkflow(
      final DirectBuffer errorCode, final ExecutableWorkflow workflow, ElementInstance instance) {

    while (instance != null && instance.isActive() && !instance.isInterrupted()) {
      final var found = findCatchEventInScope(errorCode, workflow, instance);
      if (found != null) {
        return found;
      }

      // find in parent scope if exists
      final var instanceParentKey = instance.getParentKey();
      instance = elementInstanceState.getInstance(instanceParentKey);
    }

    return null;
  }

  private CatchEventTuple findCatchEventInScope(
      final DirectBuffer errorCode,
      final ExecutableWorkflow workflow,
      final ElementInstance instance) {

    final var workflowInstanceRecord = instance.getValue();
    final var elementId = workflowInstanceRecord.getElementIdBuffer();
    final var elementType = workflowInstanceRecord.getBpmnElementType();

    final var element = workflow.getElementById(elementId, elementType, ExecutableActivity.class);

    for (final ExecutableCatchEvent catchEvent : element.getEvents()) {
      if (hasErrorCode(catchEvent, errorCode)) {

        catchEventTuple.instance = instance;
        catchEventTuple.catchEvent = catchEvent;
        return catchEventTuple;
      }
    }

    return null;
  }

  private boolean hasErrorCode(
      final ExecutableCatchEvent catchEvent, final DirectBuffer errorCode) {
    return catchEvent.isError() && catchEvent.getError().getErrorCode().equals(errorCode);
  }

  private ExecutableWorkflow getWorkflow(final long workflowKey) {

    final var deployedWorkflow = workflowState.getWorkflowByKey(workflowKey);
    if (deployedWorkflow == null) {
      throw new IllegalStateException(
          String.format(
              "Expected workflow with key '%d' to be deployed but not found", workflowKey));
    }

    return deployedWorkflow.getWorkflow();
  }

  private static class CatchEventTuple {
    private ExecutableCatchEvent catchEvent;
    private ElementInstance instance;
  }
}
