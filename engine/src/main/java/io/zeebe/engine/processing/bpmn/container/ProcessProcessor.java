/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.bpmn.container;

import io.zeebe.engine.processing.bpmn.BpmnElementContainerProcessor;
import io.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.zeebe.engine.processing.bpmn.BpmnProcessingException;
import io.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.zeebe.engine.processing.bpmn.behavior.BpmnBufferedMessageStartEventBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnEventSubscriptionBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnProcessResultSenderBehavior;
import io.zeebe.engine.processing.deployment.model.element.ExecutableFlowElementContainer;
import io.zeebe.protocol.record.intent.ProcessInstanceIntent;

public final class ProcessProcessor
    implements BpmnElementContainerProcessor<ExecutableFlowElementContainer> {

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
  public void onActivating(
      final ExecutableFlowElementContainer element, final BpmnElementContext context) {

    eventSubscriptionBehavior
        .subscribeToEvents(element, context)
        .ifRightOrLeft(
            ok -> stateTransitionBehavior.transitionToActivated(context),
            failure -> incidentBehavior.createIncident(failure, context));
  }

  @Override
  public void onActivated(
      final ExecutableFlowElementContainer element, final BpmnElementContext context) {

    boolean triggeredByEvent = false;
    if (element.hasMessageStartEvent() || element.hasTimerStartEvent()) {

      triggeredByEvent = eventSubscriptionBehavior.publishTriggeredStartEvent(context);
    }

    if (!triggeredByEvent) {

      final var noneStartEvent = element.getNoneStartEvent();
      if (noneStartEvent == null) {
        throw new BpmnProcessingException(
            context, "Expected to activate the none start event of the process but not found.");
      }

      stateTransitionBehavior.activateChildInstance(context, noneStartEvent);
    }
  }

  @Override
  public void onCompleting(
      final ExecutableFlowElementContainer element, final BpmnElementContext context) {

    eventSubscriptionBehavior.unsubscribeFromEvents(context);
    stateTransitionBehavior.transitionToCompleted(context);
  }

  @Override
  public void onCompleted(
      final ExecutableFlowElementContainer element, final BpmnElementContext context) {

    final var parentProcessInstanceKey = context.getParentProcessInstanceKey();
    if (parentProcessInstanceKey > 0) {
      // process instance is created by a call activity
      stateTransitionBehavior.onElementCompleted(element, context);
    }

    if (element.hasNoneStartEvent()) {
      processResultSenderBehavior.sendResult(context);
    }

    if (element.hasMessageStartEvent()) {
      bufferedMessageStartEventBehavior.correlateMessage(context);
    }

    stateBehavior.removeElementInstance(context);
  }

  @Override
  public void onTerminating(
      final ExecutableFlowElementContainer element, final BpmnElementContext context) {

    eventSubscriptionBehavior.unsubscribeFromEvents(context);

    final var noActiveChildInstances = stateTransitionBehavior.terminateChildInstances(context);
    if (noActiveChildInstances) {
      stateTransitionBehavior.transitionToTerminated(context);
    }
  }

  @Override
  public void onTerminated(
      final ExecutableFlowElementContainer element, final BpmnElementContext context) {

    incidentBehavior.resolveIncidents(context);

    final var parentProcessInstanceKey = context.getParentProcessInstanceKey();

    if (parentProcessInstanceKey > 0) {
      // process instance is created by a call activity
      stateTransitionBehavior.onElementTerminated(element, context);
    }

    if (element.hasMessageStartEvent()) {
      bufferedMessageStartEventBehavior.correlateMessage(context);
    }

    stateBehavior.removeElementInstance(context);
  }

  @Override
  public void onEventOccurred(
      final ExecutableFlowElementContainer element, final BpmnElementContext context) {
    throw new BpmnProcessingException(
        context,
        "Expected to handle occurred event on process, but events should not occur on process.");
  }

  @Override
  public void onChildCompleted(
      final ExecutableFlowElementContainer element,
      final BpmnElementContext flowScopeContext,
      final BpmnElementContext childContext) {

    if (stateBehavior.isLastActiveExecutionPathInScope(childContext)) {
      stateTransitionBehavior.transitionToCompleting(flowScopeContext);
    }
  }

  @Override
  public void onChildTerminated(
      final ExecutableFlowElementContainer element,
      final BpmnElementContext flowScopeContext,
      final BpmnElementContext childContext) {

    if (flowScopeContext.getIntent() == ProcessInstanceIntent.ELEMENT_TERMINATING
        && stateBehavior.isLastActiveExecutionPathInScope(childContext)) {
      stateTransitionBehavior.transitionToTerminated(flowScopeContext);

    } else {
      eventSubscriptionBehavior.publishTriggeredEventSubProcess(flowScopeContext);
    }
  }
}
