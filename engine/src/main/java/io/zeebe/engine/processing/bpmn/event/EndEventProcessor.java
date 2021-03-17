/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.bpmn.event;

import static io.zeebe.util.EnsureUtil.ensureNotNull;
import static io.zeebe.util.EnsureUtil.ensureNotNullOrEmpty;
import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.zeebe.engine.processing.bpmn.BpmnElementProcessor;
import io.zeebe.engine.processing.bpmn.BpmnProcessingException;
import io.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.zeebe.engine.processing.bpmn.behavior.BpmnEventPublicationBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.zeebe.engine.processing.common.Failure;
import io.zeebe.engine.processing.deployment.model.element.ExecutableEndEvent;
import io.zeebe.protocol.record.value.ErrorType;

public final class EndEventProcessor implements BpmnElementProcessor<ExecutableEndEvent> {

  private final BpmnEventPublicationBehavior eventPublicationBehavior;
  private final BpmnIncidentBehavior incidentBehavior;
  private final BpmnStateBehavior stateBehavior;
  private final BpmnStateTransitionBehavior stateTransitionBehavior;

  public EndEventProcessor(final BpmnBehaviors bpmnBehaviors) {
    eventPublicationBehavior = bpmnBehaviors.eventPublicationBehavior();
    incidentBehavior = bpmnBehaviors.incidentBehavior();
    stateBehavior = bpmnBehaviors.stateBehavior();
    stateTransitionBehavior = bpmnBehaviors.stateTransitionBehavior();
  }

  @Override
  public Class<ExecutableEndEvent> getType() {
    return ExecutableEndEvent.class;
  }

  @Override
  public void onActivating(final ExecutableEndEvent element, final BpmnElementContext context) {
    stateTransitionBehavior.transitionToActivated(context);
  }

  @Override
  public void onActivated(final ExecutableEndEvent element, final BpmnElementContext context) {
    if (!element.hasError()) {
      stateTransitionBehavior.transitionToCompleting(context);
      return;
    }

    final var error = element.getError();
    ensureNotNull("error", error);

    final var errorCode = error.getErrorCode();
    ensureNotNullOrEmpty("errorCode", errorCode);

    // the error must be caught at the parent or an upper scope (e.g. interrupting boundary event or
    // event sub process). This is also why we don't have to transition to the completing state here
    final boolean errorThrownAndCaught =
        eventPublicationBehavior.throwErrorEvent(errorCode, context);

    if (!errorThrownAndCaught) {
      final var errorMessage =
          String.format(
              "Expected to throw an error event with the code '%s', but it was not caught.",
              bufferAsString(errorCode));
      final var failure = new Failure(errorMessage, ErrorType.UNHANDLED_ERROR_EVENT);
      incidentBehavior.createIncident(failure, context);
    }
  }

  @Override
  public void onCompleting(final ExecutableEndEvent element, final BpmnElementContext context) {
    stateTransitionBehavior.transitionToCompleted(context);
  }

  @Override
  public void onCompleted(final ExecutableEndEvent element, final BpmnElementContext context) {
    stateTransitionBehavior.onElementCompleted(element, context);
    stateBehavior.removeElementInstance(context);
  }

  @Override
  public void onTerminating(final ExecutableEndEvent element, final BpmnElementContext context) {
    stateTransitionBehavior.transitionToTerminated(context);
  }

  @Override
  public void onTerminated(final ExecutableEndEvent element, final BpmnElementContext context) {
    incidentBehavior.resolveIncidents(context);
    stateTransitionBehavior.onElementTerminated(element, context);
    stateBehavior.removeElementInstance(context);
  }

  @Override
  public void onEventOccurred(final ExecutableEndEvent element, final BpmnElementContext context) {
    throw new BpmnProcessingException(
        context,
        "Expected to handle occurred event on end event element, but events should not occur on end event element.");
  }
}
