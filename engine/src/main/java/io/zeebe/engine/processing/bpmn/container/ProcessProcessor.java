/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.bpmn.container;

import io.zeebe.engine.processing.bpmn.BpmnElementContainerProcessor;
import io.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.zeebe.engine.processing.bpmn.BpmnProcessingException;
import io.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.zeebe.engine.processing.bpmn.behavior.BpmnBufferedMessageStartEventBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnEventSubscriptionBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnProcessResultSenderBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.zeebe.engine.processing.deployment.model.element.ExecutableFlowElementContainer;
import io.zeebe.protocol.record.intent.ProcessInstanceIntent;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

public final class ProcessProcessor
    implements BpmnElementContainerProcessor<ExecutableFlowElementContainer> {

  private static final Consumer<BpmnElementContext> NOOP = context -> {};

  private final BpmnStateBehavior stateBehavior;
  private final BpmnStateTransitionBehavior stateTransitionBehavior;
  private final BpmnEventSubscriptionBehavior eventSubscriptionBehavior;
  private final BpmnIncidentBehavior incidentBehavior;
  private final BpmnProcessResultSenderBehavior processResultSenderBehavior;
  private final BpmnBufferedMessageStartEventBehavior bufferedMessageStartEventBehavior;

  public ProcessProcessor(final BpmnBehaviors bpmnBehaviors) {
    stateBehavior = bpmnBehaviors.stateBehavior();
    stateTransitionBehavior = bpmnBehaviors.stateTransitionBehavior();
    eventSubscriptionBehavior = bpmnBehaviors.eventSubscriptionBehavior();
    incidentBehavior = bpmnBehaviors.incidentBehavior();
    processResultSenderBehavior = bpmnBehaviors.processResultSenderBehavior();
    bufferedMessageStartEventBehavior = bpmnBehaviors.bufferedMessageStartEventBehavior();
  }

  @Override
  public Class<ExecutableFlowElementContainer> getType() {
    return ExecutableFlowElementContainer.class;
  }

  @Override
  public void onActivate(
      final ExecutableFlowElementContainer element, final BpmnElementContext context) {

    eventSubscriptionBehavior
        .subscribeToEvents(element, context)
        .map(o -> stateTransitionBehavior.transitionToActivated(context))
        .ifRightOrLeft(
            activated -> activateStartEvent(element, activated),
            failure -> incidentBehavior.createIncident(failure, context));
  }

  @Override
  public void onComplete(
      final ExecutableFlowElementContainer element, final BpmnElementContext context) {

    eventSubscriptionBehavior.unsubscribeFromEvents(context);

    // we need to send the result before we transition to completed, since the
    // event applier will delete the element instance
    processResultSenderBehavior.sendResult(context);

    transitionTo(element, context, stateTransitionBehavior::transitionToCompleted);
  }

  @Override
  public void onTerminate(
      final ExecutableFlowElementContainer element, final BpmnElementContext context) {

    eventSubscriptionBehavior.unsubscribeFromEvents(context);
    incidentBehavior.resolveIncidents(context);

    final var noActiveChildInstances = stateTransitionBehavior.terminateChildInstances(context);

    if (noActiveChildInstances) {
      transitionTo(element, context, stateTransitionBehavior::transitionToTerminated);
    }
  }

  private void activateStartEvent(
      final ExecutableFlowElementContainer element, final BpmnElementContext activated) {
    if (element.hasMessageStartEvent() || element.hasTimerStartEvent()) {
      eventSubscriptionBehavior
          .getEventTriggerForProcessDefinition(activated.getProcessDefinitionKey())
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
  public void beforeExecutionPathCompleted(
      final ExecutableFlowElementContainer element,
      final BpmnElementContext flowScopeContext,
      final BpmnElementContext childContext) {}

  @Override
  public void afterExecutionPathCompleted(
      final ExecutableFlowElementContainer element,
      final BpmnElementContext flowScopeContext,
      final BpmnElementContext childContext) {

    if (stateBehavior.canBeCompleted(childContext)) {
      stateTransitionBehavior.completeElement(flowScopeContext);
    }
  }

  @Override
  public void onChildTerminated(
      final ExecutableFlowElementContainer element,
      final BpmnElementContext flowScopeContext,
      final BpmnElementContext childContext) {

    if (flowScopeContext.getIntent() != ProcessInstanceIntent.ELEMENT_TERMINATING
        && stateBehavior.isInterrupted(flowScopeContext)) {
      eventSubscriptionBehavior
          .findEventTrigger(flowScopeContext)
          .ifPresent(
              eventTrigger ->
                  eventSubscriptionBehavior.activateTriggeredEvent(
                      flowScopeContext.getElementInstanceKey(),
                      flowScopeContext.getElementInstanceKey(),
                      eventTrigger,
                      flowScopeContext));
    } else if (stateBehavior.canBeTerminated(childContext)) {
      transitionTo(element, flowScopeContext, stateTransitionBehavior::transitionToTerminated);
    }
  }

  private void transitionTo(
      final ExecutableFlowElementContainer element,
      final BpmnElementContext context,
      final UnaryOperator<BpmnElementContext> transitionOperation) {

    final var action = getPostTransitionAction(element, context);
    final var afterTransition = transitionOperation.apply(context);

    action.accept(afterTransition);
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
