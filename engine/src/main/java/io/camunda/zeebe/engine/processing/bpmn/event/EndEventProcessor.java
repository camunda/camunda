/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.event;

import static io.camunda.zeebe.util.EnsureUtil.ensureNotNull;
import static io.camunda.zeebe.util.EnsureUtil.ensureNotNullOrEmpty;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementProcessor;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnEventPublicationBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnVariableMappingBehavior;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableEndEvent;
import java.util.List;

public final class EndEventProcessor implements BpmnElementProcessor<ExecutableEndEvent> {
  private final List<EndEventBehavior> endEventBehaviors =
      List.of(
          new NoneEndEventBehavior(),
          new ErrorEndEventBehavior(),
          new MessageEndEventBehavior(),
          new TerminateEndEventBehavior());

  private final BpmnEventPublicationBehavior eventPublicationBehavior;
  private final BpmnIncidentBehavior incidentBehavior;
  private final BpmnStateTransitionBehavior stateTransitionBehavior;
  private final BpmnVariableMappingBehavior variableMappingBehavior;
  private final BpmnJobBehavior jobBehavior;
  private final BpmnStateBehavior stateBehavior;

  public EndEventProcessor(
      final BpmnBehaviors bpmnBehaviors,
      final BpmnStateTransitionBehavior stateTransitionBehavior) {
    eventPublicationBehavior = bpmnBehaviors.eventPublicationBehavior();
    incidentBehavior = bpmnBehaviors.incidentBehavior();
    this.stateTransitionBehavior = stateTransitionBehavior;
    variableMappingBehavior = bpmnBehaviors.variableMappingBehavior();
    jobBehavior = bpmnBehaviors.jobBehavior();
    stateBehavior = bpmnBehaviors.stateBehavior();
  }

  @Override
  public Class<ExecutableEndEvent> getType() {
    return ExecutableEndEvent.class;
  }

  @Override
  public void onActivate(final ExecutableEndEvent element, final BpmnElementContext activating) {
    eventBehaviorOf(element).onActivate(element, activating);
  }

  @Override
  public void onComplete(final ExecutableEndEvent element, final BpmnElementContext context) {
    eventBehaviorOf(element).onComplete(element, context);
  }

  @Override
  public void onTerminate(final ExecutableEndEvent element, final BpmnElementContext terminating) {
    eventBehaviorOf(element).onTerminate(element, terminating);

    // common behavior for all end events
    incidentBehavior.resolveIncidents(terminating);

    final var terminated = stateTransitionBehavior.transitionToTerminated(terminating);
    stateTransitionBehavior.onElementTerminated(element, terminated);
  }

  private EndEventBehavior eventBehaviorOf(final ExecutableEndEvent element) {
    return endEventBehaviors.stream()
        .filter(behavior -> behavior.isSuitableForEvent(element))
        .findFirst()
        .orElseThrow(
            () -> new UnsupportedOperationException("This kind of end event is not supported."));
  }

  /** Extract different behaviors depending on the type of event. */
  private interface EndEventBehavior {

    boolean isSuitableForEvent(final ExecutableEndEvent element);

    void onActivate(final ExecutableEndEvent element, final BpmnElementContext activating);

    default void onComplete(
        final ExecutableEndEvent element, final BpmnElementContext completing) {}

    default void onTerminate(
        final ExecutableEndEvent element, final BpmnElementContext terminating) {}
  }

  private class NoneEndEventBehavior implements EndEventBehavior {

    @Override
    public boolean isSuitableForEvent(final ExecutableEndEvent element) {
      return element.isNoneEndEvent();
    }

    @Override
    public void onActivate(final ExecutableEndEvent element, final BpmnElementContext activating) {
      final var activated = stateTransitionBehavior.transitionToActivated(activating);
      final var completing = stateTransitionBehavior.transitionToCompleting(activated);

      variableMappingBehavior
          .applyOutputMappings(completing, element)
          .flatMap(ok -> stateTransitionBehavior.transitionToCompleted(element, completing))
          .ifRightOrLeft(
              completed -> stateTransitionBehavior.takeOutgoingSequenceFlows(element, completed),
              failure -> incidentBehavior.createIncident(failure, completing));
    }
  }

  private class ErrorEndEventBehavior implements EndEventBehavior {

    @Override
    public boolean isSuitableForEvent(final ExecutableEndEvent element) {
      return element.isErrorEndEvent();
    }

    @Override
    public void onActivate(final ExecutableEndEvent element, final BpmnElementContext activating) {
      final var error = element.getError();
      ensureNotNull("error", error);

      final var errorCode = error.getErrorCode();
      ensureNotNullOrEmpty("errorCode", errorCode);

      // the error must be caught at the parent or an upper scope (e.g. interrupting boundary event
      // or
      // event sub process). This is also why we don't have to transition to the completing state
      // here
      eventPublicationBehavior
          .findErrorCatchEvent(errorCode, activating)
          .ifRightOrLeft(
              catchEvent -> {
                stateTransitionBehavior.transitionToActivated(activating);
                eventPublicationBehavior.throwErrorEvent(catchEvent);
              },
              failure -> incidentBehavior.createIncident(failure, activating));
    }
  }

  private class MessageEndEventBehavior implements EndEventBehavior {

    @Override
    public boolean isSuitableForEvent(final ExecutableEndEvent element) {
      return element.isMessageEventEvent();
    }

    @Override
    public void onActivate(final ExecutableEndEvent element, final BpmnElementContext activating) {
      variableMappingBehavior
          .applyInputMappings(activating, element)
          .flatMap(ok -> jobBehavior.createNewJob(activating, element))
          .ifRightOrLeft(
              ok -> stateTransitionBehavior.transitionToActivated(activating),
              failure -> incidentBehavior.createIncident(failure, activating));
    }

    @Override
    public void onComplete(final ExecutableEndEvent element, final BpmnElementContext completing) {
      variableMappingBehavior
          .applyOutputMappings(completing, element)
          .flatMap(ok -> stateTransitionBehavior.transitionToCompleted(element, completing))
          .ifRightOrLeft(
              completed -> stateTransitionBehavior.takeOutgoingSequenceFlows(element, completed),
              failure -> incidentBehavior.createIncident(failure, completing));
    }

    @Override
    public void onTerminate(
        final ExecutableEndEvent element, final BpmnElementContext terminating) {

      jobBehavior.cancelJob(terminating);
    }
  }

  private class TerminateEndEventBehavior implements EndEventBehavior {

    @Override
    public boolean isSuitableForEvent(final ExecutableEndEvent element) {
      return element.isTerminateEndEvent();
    }

    @Override
    public void onActivate(final ExecutableEndEvent element, final BpmnElementContext activating) {
      final var activated = stateTransitionBehavior.transitionToActivated(activating);
      final var completing = stateTransitionBehavior.transitionToCompleting(activated);
      stateTransitionBehavior
          .transitionToCompleted(element, completing)
          .ifRightOrLeft(
              completed -> {
                // terminate all other element instances of the same flow scope
                final var flowScopeContext = stateBehavior.getFlowScopeContext(completed);
                stateTransitionBehavior.terminateChildInstances(flowScopeContext);
              },
              failure -> incidentBehavior.createIncident(failure, completing));
    }
  }
}
