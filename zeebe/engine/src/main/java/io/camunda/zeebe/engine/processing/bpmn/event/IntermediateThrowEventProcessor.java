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
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnVariableMappingBehavior;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableIntermediateThrowEvent;
import io.camunda.zeebe.util.Either;
import java.util.List;
import org.agrona.DirectBuffer;

public class IntermediateThrowEventProcessor
    implements BpmnElementProcessor<ExecutableIntermediateThrowEvent> {

  private final List<IntermediateThrowEventBehavior> throwEventBehaviors =
      List.of(
          new NoneIntermediateThrowEventBehavior(),
          new MessageIntermediateThrowEventBehavior(),
          new LinkIntermediateThrowEventBehavior(),
          new EscalationIntermediateThrowEventBehavior(),
          new SignalIntermediateThrowEventBehavior(),
          new CompensationBehavior());

  private final BpmnVariableMappingBehavior variableMappingBehavior;
  private final BpmnStateTransitionBehavior stateTransitionBehavior;
  private final BpmnIncidentBehavior incidentBehavior;
  private final BpmnJobBehavior jobBehavior;
  private final BpmnEventPublicationBehavior eventPublicationBehavior;
  private final ExpressionProcessor expressionProcessor;
  private final BpmnSignalBehavior signalBehavior;
  private final BpmnCompensationSubscriptionBehaviour compensationSubscriptionBehaviour;

  public IntermediateThrowEventProcessor(
      final BpmnBehaviors bpmnBehaviors,
      final BpmnStateTransitionBehavior stateTransitionBehavior) {
    variableMappingBehavior = bpmnBehaviors.variableMappingBehavior();
    this.stateTransitionBehavior = stateTransitionBehavior;
    incidentBehavior = bpmnBehaviors.incidentBehavior();
    jobBehavior = bpmnBehaviors.jobBehavior();
    eventPublicationBehavior = bpmnBehaviors.eventPublicationBehavior();
    expressionProcessor = bpmnBehaviors.expressionBehavior();
    signalBehavior = bpmnBehaviors.signalBehavior();
    compensationSubscriptionBehaviour = bpmnBehaviors.compensationSubscriptionBehaviour();
  }

  @Override
  public Class<ExecutableIntermediateThrowEvent> getType() {
    return ExecutableIntermediateThrowEvent.class;
  }

  @Override
  public Either<Failure, ?> onActivate(
      final ExecutableIntermediateThrowEvent element, final BpmnElementContext activating) {
    return eventBehaviorOf(element).onActivate(element, activating);
  }

  @Override
  public Either<Failure, ?> finalizeActivation(
      final ExecutableIntermediateThrowEvent element, final BpmnElementContext activating) {
    return eventBehaviorOf(element).finalizeActivation(element, activating);
  }

  @Override
  public Either<Failure, ?> onComplete(
      final ExecutableIntermediateThrowEvent element, final BpmnElementContext completing) {
    return eventBehaviorOf(element).onComplete(element, completing);
  }

  @Override
  public Either<Failure, ?> finalizeCompletion(
      final ExecutableIntermediateThrowEvent element, final BpmnElementContext completing) {
    return eventBehaviorOf(element).finalizeCompletion(element, completing);
  }

  @Override
  public TransitionOutcome onTerminate(
      final ExecutableIntermediateThrowEvent element, final BpmnElementContext terminating) {
    eventBehaviorOf(element).onTerminate(element, terminating);

    if (element.hasExecutionListeners()) {
      jobBehavior.cancelJob(terminating);
    }

    // common behavior for all intermediate throw events
    final var terminated =
        stateTransitionBehavior.transitionToTerminated(terminating, element.getEventType());
    incidentBehavior.resolveIncidents(terminated);
    stateTransitionBehavior.onElementTerminated(element, terminated);
    return TransitionOutcome.CONTINUE;
  }

  private IntermediateThrowEventBehavior eventBehaviorOf(
      final ExecutableIntermediateThrowEvent element) {
    return throwEventBehaviors.stream()
        .filter(behavior -> behavior.isSuitableForEvent(element))
        .findFirst()
        .orElseThrow(
            () ->
                new UnsupportedOperationException(
                    "This kind of intermediate throw event is not supported."));
  }

  private interface IntermediateThrowEventBehavior {

    boolean isSuitableForEvent(final ExecutableIntermediateThrowEvent element);

    default Either<Failure, ?> onActivate(
        final ExecutableIntermediateThrowEvent element, final BpmnElementContext activating) {
      return SUCCESS;
    }

    default Either<Failure, ?> finalizeActivation(
        final ExecutableIntermediateThrowEvent element, final BpmnElementContext activating) {
      return SUCCESS;
    }

    default Either<Failure, ?> onComplete(
        final ExecutableIntermediateThrowEvent element, final BpmnElementContext completing) {
      return SUCCESS;
    }

    default Either<Failure, ?> finalizeCompletion(
        final ExecutableIntermediateThrowEvent element, final BpmnElementContext completing) {
      return SUCCESS;
    }

    default void onTerminate(
        final ExecutableIntermediateThrowEvent element, final BpmnElementContext terminating) {}
  }

  private final class NoneIntermediateThrowEventBehavior implements IntermediateThrowEventBehavior {

    @Override
    public boolean isSuitableForEvent(final ExecutableIntermediateThrowEvent element) {
      return element.isNoneThrowEvent();
    }

    @Override
    public Either<Failure, ?> finalizeActivation(
        final ExecutableIntermediateThrowEvent element, final BpmnElementContext activating) {
      final var activated =
          stateTransitionBehavior.transitionToActivated(activating, element.getEventType());
      stateTransitionBehavior.completeElement(activated);
      return SUCCESS;
    }

    @Override
    public Either<Failure, ?> onComplete(
        final ExecutableIntermediateThrowEvent element, final BpmnElementContext completing) {
      return variableMappingBehavior.applyOutputMappings(completing, element);
    }

    @Override
    public Either<Failure, ?> finalizeCompletion(
        final ExecutableIntermediateThrowEvent element, final BpmnElementContext completing) {
      return stateTransitionBehavior
          .transitionToCompleted(element, completing)
          .thenDo(
              completed -> stateTransitionBehavior.takeOutgoingSequenceFlows(element, completed));
    }
  }

  private final class MessageIntermediateThrowEventBehavior
      implements IntermediateThrowEventBehavior {

    @Override
    public boolean isSuitableForEvent(final ExecutableIntermediateThrowEvent element) {
      return element.isMessageThrowEvent();
    }

    @Override
    public Either<Failure, ?> onActivate(
        final ExecutableIntermediateThrowEvent element, final BpmnElementContext activating) {
      return element.getJobWorkerProperties() == null
          ? SUCCESS
          : variableMappingBehavior.applyInputMappings(activating, element);
    }

    @Override
    public Either<Failure, ?> finalizeActivation(
        final ExecutableIntermediateThrowEvent element, final BpmnElementContext activating) {
      return element.getJobWorkerProperties() == null
          ? SUCCESS
          : jobBehavior
              .evaluateJobExpressions(element.getJobWorkerProperties(), activating)
              .thenDo(
                  jobProperties -> {
                    jobBehavior.createNewJob(activating, element, jobProperties);
                    stateTransitionBehavior.transitionToActivated(
                        activating, element.getEventType());
                  });
    }

    @Override
    public Either<Failure, ?> onComplete(
        final ExecutableIntermediateThrowEvent element, final BpmnElementContext completing) {
      return variableMappingBehavior.applyOutputMappings(completing, element);
    }

    @Override
    public Either<Failure, ?> finalizeCompletion(
        final ExecutableIntermediateThrowEvent element, final BpmnElementContext completing) {
      return stateTransitionBehavior
          .transitionToCompleted(element, completing)
          .thenDo(
              completed -> stateTransitionBehavior.takeOutgoingSequenceFlows(element, completed));
    }

    @Override
    public void onTerminate(
        final ExecutableIntermediateThrowEvent element, final BpmnElementContext terminating) {
      if (element.getJobWorkerProperties() != null) {
        jobBehavior.cancelJob(terminating);
      }
    }
  }

  private final class LinkIntermediateThrowEventBehavior implements IntermediateThrowEventBehavior {
    @Override
    public boolean isSuitableForEvent(final ExecutableIntermediateThrowEvent element) {
      return element.isLinkThrowEvent();
    }

    @Override
    public Either<Failure, ?> finalizeActivation(
        final ExecutableIntermediateThrowEvent element, final BpmnElementContext activating) {
      final var activated =
          stateTransitionBehavior.transitionToActivated(activating, element.getEventType());
      stateTransitionBehavior.completeElement(activated);
      return SUCCESS;
    }

    @Override
    public Either<Failure, ?> onComplete(
        final ExecutableIntermediateThrowEvent element, final BpmnElementContext completing) {
      return variableMappingBehavior.applyOutputMappings(completing, element);
    }

    @Override
    public Either<Failure, ?> finalizeCompletion(
        final ExecutableIntermediateThrowEvent element, final BpmnElementContext completing) {
      return stateTransitionBehavior
          .transitionToCompleted(element, completing)
          .thenDo(
              completed ->
                  stateTransitionBehavior.activateElementInstanceInFlowScope(
                      completed, element.getLink().getCatchEventElement()));
    }
  }

  private final class EscalationIntermediateThrowEventBehavior
      implements IntermediateThrowEventBehavior {
    @Override
    public boolean isSuitableForEvent(final ExecutableIntermediateThrowEvent element) {
      return element.isEscalationThrowEvent();
    }

    @Override
    public Either<Failure, ?> finalizeActivation(
        final ExecutableIntermediateThrowEvent element, final BpmnElementContext activating) {
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
        final ExecutableIntermediateThrowEvent element, final BpmnElementContext completing) {
      return variableMappingBehavior.applyOutputMappings(completing, element);
    }

    @Override
    public Either<Failure, ?> finalizeCompletion(
        final ExecutableIntermediateThrowEvent element, final BpmnElementContext completing) {
      return stateTransitionBehavior
          .transitionToCompleted(element, completing)
          .thenDo(
              completed -> stateTransitionBehavior.takeOutgoingSequenceFlows(element, completed));
    }

    private Either<Failure, DirectBuffer> evaluateEscalationCode(
        final ExecutableIntermediateThrowEvent element, final BpmnElementContext context) {
      final var escalation = element.getEscalation();
      ensureNotNull("escalation", escalation);

      if (escalation.getEscalationCode().isPresent()) {
        return Either.right(escalation.getEscalationCode().get());
      }

      return expressionProcessor.evaluateStringExpressionAsDirectBuffer(
          escalation.getEscalationCodeExpression(), context.getElementInstanceKey());
    }
  }

  private final class SignalIntermediateThrowEventBehavior
      implements IntermediateThrowEventBehavior {
    @Override
    public boolean isSuitableForEvent(final ExecutableIntermediateThrowEvent element) {
      return element.isSignalThrowEvent();
    }

    @Override
    public Either<Failure, ?> onActivate(
        final ExecutableIntermediateThrowEvent element, final BpmnElementContext activating) {
      return variableMappingBehavior.applyInputMappings(activating, element);
    }

    @Override
    public Either<Failure, ?> finalizeActivation(
        final ExecutableIntermediateThrowEvent element, final BpmnElementContext activating) {
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
        final ExecutableIntermediateThrowEvent element, final BpmnElementContext completing) {
      return variableMappingBehavior.applyOutputMappings(completing, element);
    }

    @Override
    public Either<Failure, ?> finalizeCompletion(
        final ExecutableIntermediateThrowEvent element, final BpmnElementContext completing) {
      return stateTransitionBehavior
          .transitionToCompleted(element, completing)
          .thenDo(
              completed -> stateTransitionBehavior.takeOutgoingSequenceFlows(element, completed));
    }
  }

  private final class CompensationBehavior implements IntermediateThrowEventBehavior {

    @Override
    public boolean isSuitableForEvent(final ExecutableIntermediateThrowEvent element) {
      return element.isCompensationEvent();
    }

    @Override
    public Either<Failure, ?> finalizeActivation(
        final ExecutableIntermediateThrowEvent element, final BpmnElementContext activating) {
      final BpmnElementContext activated =
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
        final ExecutableIntermediateThrowEvent element, final BpmnElementContext completing) {
      return stateTransitionBehavior
          .transitionToCompleted(element, completing)
          .thenDo(
              completed -> stateTransitionBehavior.takeOutgoingSequenceFlows(element, completed));
    }
  }
}
