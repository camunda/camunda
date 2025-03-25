/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.container;

import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContainerProcessor;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.bpmn.BpmnProcessingException;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBufferedMessageStartEventBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnCompensationSubscriptionBehaviour;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnEventSubscriptionBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnProcessResultSenderBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElementContainer;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.util.Either;
import java.util.function.Consumer;
import java.util.function.Function;

public final class ProcessProcessor
    implements BpmnElementContainerProcessor<ExecutableFlowElementContainer> {

  private static final Consumer<BpmnElementContext> NOOP = context -> {};

  private final BpmnStateBehavior stateBehavior;
  private final BpmnStateTransitionBehavior stateTransitionBehavior;
  private final BpmnEventSubscriptionBehavior eventSubscriptionBehavior;
  private final BpmnIncidentBehavior incidentBehavior;
  private final BpmnProcessResultSenderBehavior processResultSenderBehavior;
  private final BpmnBufferedMessageStartEventBehavior bufferedMessageStartEventBehavior;
  private final BpmnCompensationSubscriptionBehaviour compensationSubscriptionBehaviour;
  private final BpmnJobBehavior jobBehavior;

  public ProcessProcessor(
      final BpmnBehaviors bpmnBehaviors,
      final BpmnStateTransitionBehavior stateTransitionBehavior) {
    stateBehavior = bpmnBehaviors.stateBehavior();
    this.stateTransitionBehavior = stateTransitionBehavior;
    eventSubscriptionBehavior = bpmnBehaviors.eventSubscriptionBehavior();
    incidentBehavior = bpmnBehaviors.incidentBehavior();
    processResultSenderBehavior = bpmnBehaviors.processResultSenderBehavior();
    bufferedMessageStartEventBehavior = bpmnBehaviors.bufferedMessageStartEventBehavior();
    compensationSubscriptionBehaviour = bpmnBehaviors.compensationSubscriptionBehaviour();
    jobBehavior = bpmnBehaviors.jobBehavior();
  }

  @Override
  public Class<ExecutableFlowElementContainer> getType() {
    return ExecutableFlowElementContainer.class;
  }

  @Override
  public Either<Failure, ?> finalizeActivation(
      final ExecutableFlowElementContainer element, final BpmnElementContext context) {
    final var activatedContext =
        stateTransitionBehavior.transitionToActivated(context, element.getEventType());
    activateStartEvent(element, activatedContext);
    return SUCCESS;
  }

  @Override
  public Either<Failure, ?> onComplete(
      final ExecutableFlowElementContainer element, final BpmnElementContext context) {

    eventSubscriptionBehavior.unsubscribeFromEvents(context);
    compensationSubscriptionBehaviour.deleteSubscriptionsOfProcessInstance(context);
    return SUCCESS;
  }

  @Override
  public Either<Failure, ?> finalizeCompletion(
      final ExecutableFlowElementContainer element, final BpmnElementContext context) {

    // we need to send the result before we transition to completed, since the
    // event applier will delete the element instance
    processResultSenderBehavior.sendResult(context);

    return transitionTo(
        element,
        context,
        completing -> stateTransitionBehavior.transitionToCompleted(element, completing));
  }

  @Override
  public TransitionState onTerminate(
      final ExecutableFlowElementContainer element, final BpmnElementContext context) {
    if (element.hasExecutionListeners()) {
      jobBehavior.cancelJob(context);
    }

    eventSubscriptionBehavior.unsubscribeFromEvents(context);
    incidentBehavior.resolveIncidents(context);
    compensationSubscriptionBehaviour.deleteSubscriptionsOfProcessInstance(context);

    final var noActiveChildInstances = stateTransitionBehavior.terminateChildInstances(context);

    if (noActiveChildInstances) {
      transitionTo(
          element,
          context,
          terminating ->
              Either.right(
                  stateTransitionBehavior.transitionToTerminated(
                      terminating, element.getEventType())));
    }
    return TransitionState.CONTINUE;
  }

  private void activateStartEvent(
      final ExecutableFlowElementContainer element, final BpmnElementContext activated) {
    if (element.hasMessageStartEvent()
        || element.hasTimerStartEvent()
        || element.hasSignalStartEvent()) {
      eventSubscriptionBehavior
          .getEventTriggerForProcessDefinition(activated.getProcessDefinitionKey())
          .filter(
              eventTrigger ->
                  eventTrigger.getProcessInstanceKey() == activated.getProcessInstanceKey()
                      || eventTrigger.getProcessInstanceKey() == -1L)
          .ifPresentOrElse(
              eventTrigger ->
                  eventSubscriptionBehavior.activateTriggeredStartEvent(activated, eventTrigger),
              () -> activateNoneStartEvent(element, activated));
    } else {
      activateNoneStartEvent(element, activated);
    }
  }

  private void activateNoneStartEvent(
      final ExecutableFlowElementContainer element, final BpmnElementContext context) {
    final var noneStartEvent = element.getNoneStartEvent();
    if (noneStartEvent == null) {
      throw new BpmnProcessingException(
          context, "Expected to activate the none start event of the process but not found.");
    }

    stateTransitionBehavior.activateChildInstance(context, noneStartEvent);
  }

  @Override
  public void afterExecutionPathCompleted(
      final ExecutableFlowElementContainer element,
      final BpmnElementContext flowScopeContext,
      final BpmnElementContext childContext,
      final Boolean satisfiesCompletionCondition) {

    if (stateBehavior.canBeCompleted(childContext)) {
      stateTransitionBehavior.completeElement(flowScopeContext);
    }
  }

  @Override
  public void onChildTerminated(
      final ExecutableFlowElementContainer element,
      final BpmnElementContext flowScopeContext,
      final BpmnElementContext childContext) {
    final var flowScopeInstance = stateBehavior.getElementInstance(flowScopeContext);

    if (stateBehavior.isInterrupted(flowScopeContext)) {
      final boolean interruptedByTerminateEndEvent =
          stateBehavior.isInterruptedByTerminateEndEvent(flowScopeContext, flowScopeInstance);

      if (interruptedByTerminateEndEvent && stateBehavior.canBeTerminated(childContext)) {
        // the child element instances were terminated by a terminate end event in the process
        stateTransitionBehavior.completeElement(flowScopeContext);
      } else {
        // an interrupting event subprocess was triggered
        eventSubscriptionBehavior
            .findEventTrigger(flowScopeContext)
            .ifPresent(
                eventTrigger ->
                    eventSubscriptionBehavior.activateTriggeredEvent(
                        flowScopeContext.getElementInstanceKey(),
                        flowScopeContext.getElementInstanceKey(),
                        eventTrigger,
                        flowScopeContext));
      }
    } else if (stateBehavior.canBeTerminated(childContext)) {
      if (flowScopeInstance.isTerminating()) {
        // the process instance was canceled, or interrupted by a parent process instance
        transitionTo(
            element,
            flowScopeContext,
            context ->
                Either.right(
                    stateTransitionBehavior.transitionToTerminated(
                        context, element.getEventType())));
      }
    }
  }

  private Either<Failure, ?> transitionTo(
      final ExecutableFlowElementContainer element,
      final BpmnElementContext context,
      final Function<BpmnElementContext, Either<Failure, BpmnElementContext>> transitionOperation) {

    final var postTransitionAction = getPostTransitionAction(element, context);
    return transitionOperation.apply(context).thenDo(postTransitionAction);
  }

  private Consumer<BpmnElementContext> getPostTransitionAction(
      final ExecutableFlowElementContainer processElement, final BpmnElementContext context) {

    // fetch the data before the element instance is removed while performing the transition
    final var parentProcessInstanceKey = context.getParentProcessInstanceKey();
    if (parentProcessInstanceKey > 0) {
      // the process was created by a call activity
      final var parentInstanceContext = stateBehavior.getParentElementInstanceContext(context);

      return postTransitionContext -> {
        if (postTransitionContext.getIntent() == ProcessInstanceIntent.ELEMENT_COMPLETED) {
          stateTransitionBehavior.onCalledProcessCompleted(
              postTransitionContext, parentInstanceContext);
        } else {
          stateTransitionBehavior.onCalledProcessTerminated(
              postTransitionContext, parentInstanceContext);
        }
      };

    } else if (processElement.hasMessageStartEvent()) {
      // the process might be created by a message (but not by a call activity)
      final var correlationKey = bufferedMessageStartEventBehavior.findCorrelationKey(context);

      return postTransitionContext ->
          correlationKey.ifPresent(
              key ->
                  bufferedMessageStartEventBehavior.correlateMessage(postTransitionContext, key));

    } else {
      return NOOP;
    }
  }
}
