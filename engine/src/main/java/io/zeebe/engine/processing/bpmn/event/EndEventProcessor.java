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

import io.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.zeebe.engine.processing.bpmn.BpmnElementProcessor;
import io.zeebe.engine.processing.bpmn.BpmnProcessingException;
import io.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.zeebe.engine.processing.bpmn.behavior.BpmnEventPublicationBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.zeebe.engine.processing.deployment.model.element.ExecutableEndEvent;
import io.zeebe.protocol.record.intent.ProcessInstanceIntent;

public final class EndEventProcessor implements BpmnElementProcessor<ExecutableEndEvent> {
  private static final String TRANSITION_TO_COMPLETED_PRECONDITION_ERROR =
      "Expected to transition element to completed, but state is not ELEMENT_ACTIVATING";

  private final BpmnEventPublicationBehavior eventPublicationBehavior;
  private final BpmnIncidentBehavior incidentBehavior;
  private final BpmnStateTransitionBehavior stateTransitionBehavior;

  public EndEventProcessor(final BpmnBehaviors bpmnBehaviors) {
    eventPublicationBehavior = bpmnBehaviors.eventPublicationBehavior();
    incidentBehavior = bpmnBehaviors.incidentBehavior();
    stateTransitionBehavior = bpmnBehaviors.stateTransitionBehavior();
  }

  @Override
  public Class<ExecutableEndEvent> getType() {
    return ExecutableEndEvent.class;
  }

  @Override
  public void onActivate(final ExecutableEndEvent element, final BpmnElementContext activating) {
    if (!element.hasError()) {
      transitionUntilCompleted(element, activating);
      return;
    }

    final var error = element.getError();
    ensureNotNull("error", error);

    final var errorCode = error.getErrorCode();
    ensureNotNullOrEmpty("errorCode", errorCode);

    // the error must be caught at the parent or an upper scope (e.g. interrupting boundary event or
    // event sub process). This is also why we don't have to transition to the completing state here
    eventPublicationBehavior
        .findErrorCatchEvent(errorCode, activating)
        .ifRightOrLeft(
            catchEvent -> {
              stateTransitionBehavior.transitionToActivated(activating);
              eventPublicationBehavior.throwErrorEvent(catchEvent);
            },
            failure -> incidentBehavior.createIncident(failure, activating));
  }

  @Override
  public void onTerminate(final ExecutableEndEvent element, final BpmnElementContext terminating) {
    incidentBehavior.resolveIncidents(terminating);

    final var terminated = stateTransitionBehavior.transitionToTerminated(terminating);
    stateTransitionBehavior.onElementTerminated(element, terminated);
  }

  // there's some duplication here with ExclusiveGatewayProcessor where we want to short circuit and
  // go directly from activated -> completed, which could be dry'd up
  // TODO(npepinpe): candidate for clean up for https://github.com/camunda-cloud/zeebe/issues/6202
  private void transitionUntilCompleted(
      final ExecutableEndEvent element, final BpmnElementContext activating) {
    if (activating.getIntent() != ProcessInstanceIntent.ELEMENT_ACTIVATING) {
      throw new BpmnProcessingException(activating, TRANSITION_TO_COMPLETED_PRECONDITION_ERROR);
    }

    final var activated = stateTransitionBehavior.transitionToActivated(activating);
    final var completing = stateTransitionBehavior.transitionToCompleting(activated);

    stateTransitionBehavior.transitionToCompletedWithParentNotification(element, completing);
  }
}
