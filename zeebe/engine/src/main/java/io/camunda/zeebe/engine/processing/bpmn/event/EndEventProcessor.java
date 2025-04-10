/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.event;

import static io.camunda.zeebe.util.EnsureUtil.ensureNotNull;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementProcessor;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnCompensationSubscriptionBehaviour;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnEventPublicationBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnSignalBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnVariableMappingBehavior;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableEndEvent;
import io.camunda.zeebe.util.Either;
import java.util.List;
import org.agrona.DirectBuffer;

public final class EndEventProcessor implements BpmnElementProcessor<ExecutableEndEvent> {
  private final List<EndEventBehavior> endEventBehaviors =
      List.of(
          new NoneEndEventBehavior(),
          new ErrorEndEventBehavior(),
          new MessageEndEventBehavior(),
          new TerminateEndEventBehavior(),
          new EscalationEndEventBehavior(),
          new SignalEndEventBehavior(),
          new CompensationBehaviour());

  private final ExpressionProcessor expressionProcessor;
  private final BpmnEventPublicationBehavior eventPublicationBehavior;
  private final BpmnIncidentBehavior incidentBehavior;
  private final BpmnStateTransitionBehavior stateTransitionBehavior;
  private final BpmnVariableMappingBehavior variableMappingBehavior;
  private final BpmnJobBehavior jobBehavior;
  private final BpmnSignalBehavior signalBehavior;
  private final BpmnStateBehavior stateBehavior;
  private final BpmnCompensationSubscriptionBehaviour compensationSubscriptionBehaviour;

  public EndEventProcessor(
      final BpmnBehaviors bpmnBehaviors,
      final BpmnStateTransitionBehavior stateTransitionBehavior) {
    expressionProcessor = bpmnBehaviors.expressionBehavior();
    eventPublicationBehavior = bpmnBehaviors.eventPublicationBehavior();
    incidentBehavior = bpmnBehaviors.incidentBehavior();
    this.stateTransitionBehavior = stateTransitionBehavior;
    variableMappingBehavior = bpmnBehaviors.variableMappingBehavior();
    jobBehavior = bpmnBehaviors.jobBehavior();
    stateBehavior = bpmnBehaviors.stateBehavior();
    signalBehavior = bpmnBehaviors.signalBehavior();
    compensationSubscriptionBehaviour = bpmnBehaviors.compensationSubscriptionBehaviour();
  }

  @Override
  public Class<ExecutableEndEvent> getType() {
    return ExecutableEndEvent.class;
  }

  @Override
  public Either<Failure, ?> onActivate(
      final ExecutableEndEvent element, final BpmnElementContext activating) {
    return eventBehaviorOf(element).onActivate(element, activating);
  }

  @Override
  public Either<Failure, ?> finalizeActivation(
      final ExecutableEndEvent element, final BpmnElementContext activating) {
    return eventBehaviorOf(element).finalizeActivation(element, activating);
  }

  @Override
  public Either<Failure, ?> onComplete(
      final ExecutableEndEvent element, final BpmnElementContext context) {
    return eventBehaviorOf(element).onComplete(element, context);
  }

  @Override
  public Either<Failure, ?> finalizeCompletion(
      final ExecutableEndEvent element, final BpmnElementContext context) {
    return eventBehaviorOf(element).finalizeCompletion(element, context);
  }

  @Override
  public TransitionOutcome onTerminate(
      final ExecutableEndEvent element, final BpmnElementContext terminating) {
    eventBehaviorOf(element).onTerminate(element, terminating);

    if (element.hasExecutionListeners()) {
      jobBehavior.cancelJob(terminating);
    }

    // common behavior for all end events
    incidentBehavior.resolveIncidents(terminating);

    final var terminated =
        stateTransitionBehavior.transitionToTerminated(terminating, element.getEventType());
    stateTransitionBehavior.onElementTerminated(element, terminated);
    return TransitionOutcome.CONTINUE;
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

    default Either<Failure, ?> onActivate(
        final ExecutableEndEvent element, final BpmnElementContext activating) {
      return SUCCESS;
    }

    default Either<Failure, ?> finalizeActivation(
        final ExecutableEndEvent element, final BpmnElementContext activating) {
      return SUCCESS;
    }

    default Either<Failure, ?> onComplete(
        final ExecutableEndEvent element, final BpmnElementContext completing) {
      return SUCCESS;
    }

    default Either<Failure, ?> finalizeCompletion(
        final ExecutableEndEvent element, final BpmnElementContext completing) {
      return SUCCESS;
    }

    default void onTerminate(
        final ExecutableEndEvent element, final BpmnElementContext terminating) {}
  }

  private final class NoneEndEventBehavior implements EndEventBehavior {

    @Override
    public boolean isSuitableForEvent(final ExecutableEndEvent element) {
      return element.isNoneEndEvent();
    }

    @Override
    public Either<Failure, ?> finalizeActivation(
        final ExecutableEndEvent element, final BpmnElementContext activating) {
      final var activated =
          stateTransitionBehavior.transitionToActivated(activating, element.getEventType());
      stateTransitionBehavior.completeElement(activated);
      return SUCCESS;
    }

    @Override
    public Either<Failure, ?> onComplete(
        final ExecutableEndEvent element, final BpmnElementContext completing) {
      return variableMappingBehavior.applyOutputMappings(completing, element);
    }

    @Override
    public Either<Failure, ?> finalizeCompletion(
        final ExecutableEndEvent element, final BpmnElementContext completing) {
      return stateTransitionBehavior
          .transitionToCompleted(element, completing)
          .thenDo(
              completed -> stateTransitionBehavior.takeOutgoingSequenceFlows(element, completed));
    }
  }

  private final class ErrorEndEventBehavior implements EndEventBehavior {

    @Override
    public boolean isSuitableForEvent(final ExecutableEndEvent element) {
      return element.isErrorEndEvent();
    }

    @Override
    public Either<Failure, ?> finalizeActivation(
        final ExecutableEndEvent element, final BpmnElementContext activating) {

      // the error must be caught at the parent or an upper scope
      // (e.g. interrupting boundary event or event sub process).
      // This is also why we don't have to transition to the completing state here
      return evaluateErrorCode(element, activating)
          .flatMap(errorCode -> eventPublicationBehavior.findErrorCatchEvent(errorCode, activating))
          .thenDo(
              catchEvent -> {
                stateTransitionBehavior.transitionToActivated(activating, element.getEventType());
                eventPublicationBehavior.throwErrorEvent(catchEvent);
              });
    }

    private Either<Failure, DirectBuffer> evaluateErrorCode(
        final ExecutableEndEvent element, final BpmnElementContext context) {
      final var error = element.getError();
      ensureNotNull("error", error);

      if (error.getErrorCode().isPresent()) {
        return Either.right(error.getErrorCode().get());
      }

      return expressionProcessor.evaluateStringExpressionAsDirectBuffer(
          error.getErrorCodeExpression(), context.getElementInstanceKey());
    }
  }

  private final class MessageEndEventBehavior implements EndEventBehavior {

    @Override
    public boolean isSuitableForEvent(final ExecutableEndEvent element) {
      return element.isMessageEndEvent();
    }

    @Override
    public Either<Failure, ?> onActivate(
        final ExecutableEndEvent element, final BpmnElementContext activating) {
      return variableMappingBehavior.applyInputMappings(activating, element);
    }

    @Override
    public Either<Failure, ?> finalizeActivation(
        final ExecutableEndEvent element, final BpmnElementContext activating) {
      return jobBehavior
          .evaluateJobExpressions(element.getJobWorkerProperties(), activating)
          .thenDo(
              jobProperties -> {
                jobBehavior.createNewJob(activating, element, jobProperties);
                stateTransitionBehavior.transitionToActivated(activating, element.getEventType());
              });
    }

    @Override
    public Either<Failure, ?> onComplete(
        final ExecutableEndEvent element, final BpmnElementContext completing) {
      return variableMappingBehavior.applyOutputMappings(completing, element);
    }

    @Override
    public Either<Failure, ?> finalizeCompletion(
        final ExecutableEndEvent element, final BpmnElementContext completing) {
      return stateTransitionBehavior
          .transitionToCompleted(element, completing)
          .thenDo(
              completed -> stateTransitionBehavior.takeOutgoingSequenceFlows(element, completed));
    }

    @Override
    public void onTerminate(
        final ExecutableEndEvent element, final BpmnElementContext terminating) {
      jobBehavior.cancelJob(terminating);
    }
  }

  private final class TerminateEndEventBehavior implements EndEventBehavior {

    @Override
    public boolean isSuitableForEvent(final ExecutableEndEvent element) {
      return element.isTerminateEndEvent();
    }

    @Override
    public Either<Failure, ?> finalizeActivation(
        final ExecutableEndEvent element, final BpmnElementContext activating) {
      final var activated =
          stateTransitionBehavior.transitionToActivated(activating, element.getEventType());
      stateTransitionBehavior.completeElement(activated);
      return SUCCESS;
    }

    @Override
    public Either<Failure, ?> finalizeCompletion(
        final ExecutableEndEvent element, final BpmnElementContext completing) {
      return stateTransitionBehavior
          .transitionToCompleted(element, completing)
          .thenDo(
              completed -> {
                // terminate all other element instances of the same flow scope
                final var flowScopeContext = stateBehavior.getFlowScopeContext(completed);
                stateTransitionBehavior.terminateChildInstances(flowScopeContext);
              });
    }
  }

  private final class EscalationEndEventBehavior implements EndEventBehavior {
    @Override
    public boolean isSuitableForEvent(final ExecutableEndEvent element) {
      return element.isEscalationEndEvent();
    }

    @Override
    public Either<Failure, ?> finalizeActivation(
        final ExecutableEndEvent element, final BpmnElementContext activating) {
      return evaluateEscalationCode(element, activating)
          .thenDo(
              escalationCode -> {
                final var activated =
                    stateTransitionBehavior.transitionToActivated(
                        activating, element.getEventType());
                final boolean canBeCompleted =
                    eventPublicationBehavior.throwEscalationEvent(
                        element.getId(), escalationCode, activated);

                if (canBeCompleted) {
                  stateTransitionBehavior.completeElement(activated);
                }
              });
    }

    @Override
    public Either<Failure, ?> onComplete(
        final ExecutableEndEvent element, final BpmnElementContext completing) {
      return variableMappingBehavior.applyOutputMappings(completing, element);
    }

    @Override
    public Either<Failure, ?> finalizeCompletion(
        final ExecutableEndEvent element, final BpmnElementContext completing) {
      return stateTransitionBehavior
          .transitionToCompleted(element, completing)
          .thenDo(
              completed -> stateTransitionBehavior.takeOutgoingSequenceFlows(element, completed));
    }

    private Either<Failure, DirectBuffer> evaluateEscalationCode(
        final ExecutableEndEvent element, final BpmnElementContext context) {
      final var escalation = element.getEscalation();
      ensureNotNull("escalation", escalation);

      if (escalation.getEscalationCode().isPresent()) {
        return Either.right(escalation.getEscalationCode().get());
      }

      return expressionProcessor.evaluateStringExpressionAsDirectBuffer(
          escalation.getEscalationCodeExpression(), context.getElementInstanceKey());
    }
  }

  private final class SignalEndEventBehavior implements EndEventBehavior {
    @Override
    public boolean isSuitableForEvent(final ExecutableEndEvent element) {
      return element.isSignalEndEvent();
    }

    @Override
    public Either<Failure, ?> onActivate(
        final ExecutableEndEvent element, final BpmnElementContext activating) {
      return variableMappingBehavior.applyInputMappings(activating, element);
    }

    @Override
    public Either<Failure, ?> finalizeActivation(
        final ExecutableEndEvent element, final BpmnElementContext activating) {
      return signalBehavior
          .broadcastNewSignal(activating, element.getSignal())
          .thenDo(
              ok -> {
                final var activated =
                    stateTransitionBehavior.transitionToActivated(
                        activating, element.getEventType());
                stateTransitionBehavior.completeElement(activated);
              });
    }

    @Override
    public Either<Failure, ?> onComplete(
        final ExecutableEndEvent element, final BpmnElementContext completing) {
      return variableMappingBehavior.applyOutputMappings(completing, element);
    }

    @Override
    public Either<Failure, ?> finalizeCompletion(
        final ExecutableEndEvent element, final BpmnElementContext completing) {
      return stateTransitionBehavior
          .transitionToCompleted(element, completing)
          .thenDo(
              completed -> stateTransitionBehavior.takeOutgoingSequenceFlows(element, completed));
    }
  }

  private final class CompensationBehaviour implements EndEventBehavior {

    @Override
    public boolean isSuitableForEvent(final ExecutableEndEvent element) {
      return element.isCompensationEvent();
    }

    @Override
    public Either<Failure, ?> finalizeActivation(
        final ExecutableEndEvent element, final BpmnElementContext activating) {
      final var activated =
          stateTransitionBehavior.transitionToActivated(activating, element.getEventType());

      final var compensation = element.getCompensation();
      final var isCompensationTriggered =
          compensation.hasReferenceActivity()
              ? compensationSubscriptionBehaviour.triggerCompensationForActivity(
                  element, compensation.getReferenceCompensationActivity(), activated)
              : compensationSubscriptionBehaviour.triggerCompensation(element, activating);

      if (isCompensationTriggered) {
        return SUCCESS;
      }

      stateTransitionBehavior.completeElement(activated);
      return SUCCESS;
    }

    @Override
    public Either<Failure, ?> finalizeCompletion(
        final ExecutableEndEvent element, final BpmnElementContext completing) {
      return stateTransitionBehavior
          .transitionToCompleted(element, completing)
          .thenDo(
              completed -> stateTransitionBehavior.takeOutgoingSequenceFlows(element, completed));
    }
  }
}
