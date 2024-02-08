/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
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
  private final BpmnStateBehavior stateBehavior;
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
    stateBehavior = bpmnBehaviors.stateBehavior();
    compensationSubscriptionBehaviour = bpmnBehaviors.compensationSubscriptionBehaviour();
  }

  @Override
  public Class<ExecutableIntermediateThrowEvent> getType() {
    return ExecutableIntermediateThrowEvent.class;
  }

  @Override
  public void onActivate(
      final ExecutableIntermediateThrowEvent element, final BpmnElementContext activating) {
    eventBehaviorOf(element).onActivate(element, activating);
  }

  @Override
  public void onComplete(
      final ExecutableIntermediateThrowEvent element, final BpmnElementContext completing) {
    eventBehaviorOf(element).onComplete(element, completing);
  }

  @Override
  public void onTerminate(
      final ExecutableIntermediateThrowEvent element, final BpmnElementContext terminating) {
    eventBehaviorOf(element).onTerminate(element, terminating);

    // common behavior for all intermediate throw events
    final var terminated =
        stateTransitionBehavior.transitionToTerminated(terminating, element.getEventType());
    incidentBehavior.resolveIncidents(terminated);
    stateTransitionBehavior.onElementTerminated(element, terminated);
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

    void onActivate(
        final ExecutableIntermediateThrowEvent element, final BpmnElementContext activating);

    default void onComplete(
        final ExecutableIntermediateThrowEvent element, final BpmnElementContext completing) {}

    default void onTerminate(
        final ExecutableIntermediateThrowEvent element, final BpmnElementContext terminating) {}
  }

  private final class NoneIntermediateThrowEventBehavior implements IntermediateThrowEventBehavior {

    @Override
    public boolean isSuitableForEvent(final ExecutableIntermediateThrowEvent element) {
      return element.isNoneThrowEvent();
    }

    @Override
    public void onActivate(
        final ExecutableIntermediateThrowEvent element, final BpmnElementContext activating) {
      final var activated =
          stateTransitionBehavior.transitionToActivated(activating, element.getEventType());
      stateTransitionBehavior.completeElement(activated);
    }

    @Override
    public void onComplete(
        final ExecutableIntermediateThrowEvent element, final BpmnElementContext completing) {
      variableMappingBehavior
          .applyOutputMappings(completing, element)
          .flatMap(ok -> stateTransitionBehavior.transitionToCompleted(element, completing))
          .ifRightOrLeft(
              completed -> stateTransitionBehavior.takeOutgoingSequenceFlows(element, completed),
              failure -> incidentBehavior.createIncident(failure, completing));
    }
  }

  private final class MessageIntermediateThrowEventBehavior
      implements IntermediateThrowEventBehavior {

    @Override
    public boolean isSuitableForEvent(final ExecutableIntermediateThrowEvent element) {
      return element.isMessageThrowEvent();
    }

    @Override
    public void onActivate(
        final ExecutableIntermediateThrowEvent element, final BpmnElementContext activating) {
      if (element.getJobWorkerProperties() != null) {
        variableMappingBehavior
            .applyInputMappings(activating, element)
            .flatMap(ok -> jobBehavior.evaluateJobExpressions(element, activating))
            .ifRightOrLeft(
                jobProperties -> {
                  jobBehavior.createNewJob(activating, element, jobProperties);
                  stateTransitionBehavior.transitionToActivated(activating, element.getEventType());
                },
                failure -> incidentBehavior.createIncident(failure, activating));
      }
    }

    @Override
    public void onComplete(
        final ExecutableIntermediateThrowEvent element, final BpmnElementContext completing) {
      variableMappingBehavior
          .applyOutputMappings(completing, element)
          .flatMap(ok -> stateTransitionBehavior.transitionToCompleted(element, completing))
          .ifRightOrLeft(
              completed -> stateTransitionBehavior.takeOutgoingSequenceFlows(element, completed),
              failure -> incidentBehavior.createIncident(failure, completing));
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
    public void onActivate(
        final ExecutableIntermediateThrowEvent element, final BpmnElementContext activating) {
      final var activated =
          stateTransitionBehavior.transitionToActivated(activating, element.getEventType());
      stateTransitionBehavior.completeElement(activated);
    }

    @Override
    public void onComplete(
        final ExecutableIntermediateThrowEvent element, final BpmnElementContext completing) {
      final var link = element.getLink();
      variableMappingBehavior
          .applyOutputMappings(completing, element)
          .flatMap(ok -> stateTransitionBehavior.transitionToCompleted(element, completing))
          .ifRightOrLeft(
              completed ->
                  stateTransitionBehavior.activateElementInstanceInFlowScope(
                      completed, link.getCatchEventElement()),
              failure -> incidentBehavior.createIncident(failure, completing));
    }
  }

  private final class EscalationIntermediateThrowEventBehavior
      implements IntermediateThrowEventBehavior {
    @Override
    public boolean isSuitableForEvent(final ExecutableIntermediateThrowEvent element) {
      return element.isEscalationThrowEvent();
    }

    @Override
    public void onActivate(
        final ExecutableIntermediateThrowEvent element, final BpmnElementContext activating) {
      evaluateEscalationCode(element, activating)
          .ifRightOrLeft(
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
              },
              failure -> incidentBehavior.createIncident(failure, activating));
    }

    @Override
    public void onComplete(
        final ExecutableIntermediateThrowEvent element, final BpmnElementContext completing) {
      variableMappingBehavior
          .applyOutputMappings(completing, element)
          .flatMap(ok -> stateTransitionBehavior.transitionToCompleted(element, completing))
          .ifRightOrLeft(
              completed -> stateTransitionBehavior.takeOutgoingSequenceFlows(element, completed),
              failure -> incidentBehavior.createIncident(failure, completing));
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
    public void onActivate(
        final ExecutableIntermediateThrowEvent element, final BpmnElementContext activating) {
      variableMappingBehavior
          .applyInputMappings(activating, element)
          .flatMap(ok -> signalBehavior.broadcastNewSignal(activating, element.getSignal()))
          .ifRightOrLeft(
              ok -> {
                final var activated =
                    stateTransitionBehavior.transitionToActivated(
                        activating, element.getEventType());
                stateTransitionBehavior.completeElement(activated);
              },
              failure -> incidentBehavior.createIncident(failure, activating));
    }

    @Override
    public void onComplete(
        final ExecutableIntermediateThrowEvent element, final BpmnElementContext completing) {
      variableMappingBehavior
          .applyOutputMappings(completing, element)
          .flatMap(ok -> stateTransitionBehavior.transitionToCompleted(element, completing))
          .ifRightOrLeft(
              completed -> stateTransitionBehavior.takeOutgoingSequenceFlows(element, completed),
              failure -> incidentBehavior.createIncident(failure, completing));
    }
  }

  private final class CompensationBehavior implements IntermediateThrowEventBehavior {

    @Override
    public boolean isSuitableForEvent(final ExecutableIntermediateThrowEvent element) {
      return element.isCompensationEvent();
    }

    @Override
    public void onActivate(
        final ExecutableIntermediateThrowEvent element, final BpmnElementContext activating) {
      final BpmnElementContext activated =
          stateTransitionBehavior.transitionToActivated(activating, element.getEventType());

      final var isCompensationTriggered =
          compensationSubscriptionBehaviour.triggerCompensation(element, activating);

      if (!isCompensationTriggered) {
        final var completing = stateTransitionBehavior.transitionToCompleting(activated);
        onComplete(element, completing);
      }
    }

    @Override
    public void onComplete(
        final ExecutableIntermediateThrowEvent element, final BpmnElementContext completing) {
      stateTransitionBehavior
          .transitionToCompleted(element, completing)
          .ifRightOrLeft(
              completed -> stateTransitionBehavior.takeOutgoingSequenceFlows(element, completed),
              failure -> incidentBehavior.createIncident(failure, completing));
    }
  }
}
