/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.event;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementProcessor;
import io.camunda.zeebe.engine.processing.bpmn.BpmnProcessingException;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnVariableMappingBehavior;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableBoundaryEvent;
import io.camunda.zeebe.util.Either;

public final class BoundaryEventProcessor implements BpmnElementProcessor<ExecutableBoundaryEvent> {

  private final BpmnStateTransitionBehavior stateTransitionBehavior;
  private final BpmnVariableMappingBehavior variableMappingBehavior;
  private final BpmnIncidentBehavior incidentBehavior;
  private final BpmnJobBehavior jobBehavior;

  public BoundaryEventProcessor(
      final BpmnBehaviors bpmnBehaviors,
      final BpmnStateTransitionBehavior stateTransitionBehavior) {
    this.stateTransitionBehavior = stateTransitionBehavior;
    variableMappingBehavior = bpmnBehaviors.variableMappingBehavior();
    incidentBehavior = bpmnBehaviors.incidentBehavior();
    jobBehavior = bpmnBehaviors.jobBehavior();
  }

  @Override
  public Class<ExecutableBoundaryEvent> getType() {
    return ExecutableBoundaryEvent.class;
  }

  @Override
  public Either<Failure, ?> onActivate(
      final ExecutableBoundaryEvent element, final BpmnElementContext context) {
    // the boundary event is activated by writing an ACTIVATING and ACTIVATED event to pass the
    // variables from the event for the output mapping
    throw new BpmnProcessingException(
        context,
        "Expected an ACTIVATING and ACTIVATED event for the boundary event but found an ACTIVATE command.");
  }

  @Override
  public Either<Failure, ?> onComplete(
      final ExecutableBoundaryEvent element, final BpmnElementContext context) {
    return variableMappingBehavior.applyOutputMappings(context, element);
  }

  @Override
  public Either<Failure, ?> finalizeCompletion(
      final ExecutableBoundaryEvent element, final BpmnElementContext context) {

    return stateTransitionBehavior
        .transitionToCompleted(element, context)
        .thenDo(completed -> stateTransitionBehavior.takeOutgoingSequenceFlows(element, completed));
  }

  @Override
  public TransitionOutcome onTerminate(
      final ExecutableBoundaryEvent element, final BpmnElementContext context) {
    if (element.hasExecutionListeners()) {
      jobBehavior.cancelJob(context);
    }

    incidentBehavior.resolveIncidents(context);

    final var terminated =
        stateTransitionBehavior.transitionToTerminated(context, element.getEventType());
    stateTransitionBehavior.onElementTerminated(element, terminated);
    return TransitionOutcome.CONTINUE;
  }
}
