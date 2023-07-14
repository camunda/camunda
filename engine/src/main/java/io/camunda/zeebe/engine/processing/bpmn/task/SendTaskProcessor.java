/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.task;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementProcessor;
import io.camunda.zeebe.engine.processing.bpmn.BpmnProcessingException;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnEventSubscriptionBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnMessageBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnVariableMappingBehavior;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableSendTask;

public final class SendTaskProcessor implements BpmnElementProcessor<ExecutableSendTask> {

  private final SendTaskBehavior publishMessageBehavior;
  private final SendTaskBehavior jobWorkerTaskBehavior;

  public SendTaskProcessor(
      final BpmnBehaviors bpmnBehaviors,
      final BpmnStateTransitionBehavior stateTransitionBehavior) {
    publishMessageBehavior = new PublishMessageBehavior(bpmnBehaviors, stateTransitionBehavior);
    jobWorkerTaskBehavior = new JobWorkerTaskBehavior(bpmnBehaviors, stateTransitionBehavior);
  }

  @Override
  public Class<ExecutableSendTask> getType() {
    return ExecutableSendTask.class;
  }

  @Override
  public void onActivate(final ExecutableSendTask element, final BpmnElementContext context) {
    eventBehaviorOf(element, context).onActivate(element, context);
  }

  @Override
  public void onComplete(final ExecutableSendTask element, final BpmnElementContext context) {
    eventBehaviorOf(element, context).onComplete(element, context);
  }

  @Override
  public void onTerminate(final ExecutableSendTask element, final BpmnElementContext context) {
    eventBehaviorOf(element, context).onTerminate(element, context);
  }

  private SendTaskBehavior eventBehaviorOf(
      final ExecutableSendTask element, final BpmnElementContext context) {
    if (element.getPublishMessageProperties() != null) {
      return publishMessageBehavior;
    } else if (element.getJobWorkerProperties() != null) {
      return jobWorkerTaskBehavior;
    } else {
      throw new BpmnProcessingException(
          context, "Expected to process send task, but could not determine processing behavior");
    }
  }

  private static final class PublishMessageBehavior implements SendTaskBehavior {

    private final BpmnMessageBehavior messageBehavior;
    private final BpmnEventSubscriptionBehavior eventSubscriptionBehavior;
    private final BpmnIncidentBehavior incidentBehavior;
    private final BpmnStateTransitionBehavior stateTransitionBehavior;
    private final BpmnVariableMappingBehavior variableMappingBehavior;
    private final BpmnStateBehavior stateBehavior;

    public PublishMessageBehavior(
        final BpmnBehaviors bpmnBehaviors,
        final BpmnStateTransitionBehavior stateTransitionBehavior) {
      messageBehavior = bpmnBehaviors.messageBehavior();
      eventSubscriptionBehavior = bpmnBehaviors.eventSubscriptionBehavior();
      incidentBehavior = bpmnBehaviors.incidentBehavior();
      this.stateTransitionBehavior = stateTransitionBehavior;
      variableMappingBehavior = bpmnBehaviors.variableMappingBehavior();
      stateBehavior = bpmnBehaviors.stateBehavior();
    }

    @Override
    public void onActivate(final ExecutableSendTask element, final BpmnElementContext context) {
      variableMappingBehavior
          .applyInputMappings(context, element)
          .flatMap(ok -> eventSubscriptionBehavior.subscribeToEvents(element, context))
          .flatMap(ok -> messageBehavior.publishMessage(element, context))
          .ifRightOrLeft(
              ok -> {
                final var activated = stateTransitionBehavior.transitionToActivated(context);
                stateTransitionBehavior.completeElement(activated);
              },
              failure -> incidentBehavior.createIncident(failure, context));
    }

    @Override
    public void onComplete(final ExecutableSendTask element, final BpmnElementContext context) {
      variableMappingBehavior
          .applyOutputMappings(context, element)
          .flatMap(
              ok -> {
                eventSubscriptionBehavior.unsubscribeFromEvents(context);
                return stateTransitionBehavior.transitionToCompleted(element, context);
              })
          .ifRightOrLeft(
              completed -> stateTransitionBehavior.takeOutgoingSequenceFlows(element, completed),
              failure -> incidentBehavior.createIncident(failure, context));
    }

    @Override
    public void onTerminate(final ExecutableSendTask element, final BpmnElementContext context) {
      final var flowScopeInstance = stateBehavior.getFlowScopeInstance(context);

      eventSubscriptionBehavior.unsubscribeFromEvents(context);
      incidentBehavior.resolveIncidents(context);

      eventSubscriptionBehavior
          .findEventTrigger(context)
          .filter(eventTrigger -> flowScopeInstance.isActive())
          .filter(eventTrigger -> !flowScopeInstance.isInterrupted())
          .ifPresentOrElse(
              eventTrigger -> {
                final var terminated = stateTransitionBehavior.transitionToTerminated(context);
                eventSubscriptionBehavior.activateTriggeredEvent(
                    context.getElementInstanceKey(),
                    terminated.getFlowScopeKey(),
                    eventTrigger,
                    terminated);
              },
              () -> {
                final var terminated = stateTransitionBehavior.transitionToTerminated(context);
                stateTransitionBehavior.onElementTerminated(element, terminated);
              });
    }
  }

  private static final class JobWorkerTaskBehavior implements SendTaskBehavior {

    private final JobWorkerTaskProcessor delegate;

    public JobWorkerTaskBehavior(
        final BpmnBehaviors bpmnBehaviors,
        final BpmnStateTransitionBehavior stateTransitionBehavior) {
      delegate = new JobWorkerTaskProcessor(bpmnBehaviors, stateTransitionBehavior);
    }

    @Override
    public void onActivate(final ExecutableSendTask element, final BpmnElementContext activating) {
      delegate.onActivate(element, activating);
    }

    @Override
    public void onComplete(final ExecutableSendTask element, final BpmnElementContext completing) {
      delegate.onComplete(element, completing);
    }

    @Override
    public void onTerminate(
        final ExecutableSendTask element, final BpmnElementContext terminating) {
      delegate.onTerminate(element, terminating);
    }
  }

  /** Extract different behaviors depending on the type of task. */
  private interface SendTaskBehavior {

    void onActivate(ExecutableSendTask element, BpmnElementContext activating);

    void onComplete(ExecutableSendTask element, BpmnElementContext completing);

    void onTerminate(ExecutableSendTask element, BpmnElementContext terminating);
  }
}
