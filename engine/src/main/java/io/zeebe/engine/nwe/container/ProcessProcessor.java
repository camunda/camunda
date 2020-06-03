/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.nwe.container;

import io.zeebe.engine.nwe.BpmnElementContainerProcessor;
import io.zeebe.engine.nwe.BpmnElementContext;
import io.zeebe.engine.nwe.BpmnProcessingException;
import io.zeebe.engine.nwe.behavior.BpmnBehaviors;
import io.zeebe.engine.nwe.behavior.BpmnBufferedMessageStartEventBehavior;
import io.zeebe.engine.nwe.behavior.BpmnEventSubscriptionBehavior;
import io.zeebe.engine.nwe.behavior.BpmnIncidentBehavior;
import io.zeebe.engine.nwe.behavior.BpmnStateBehavior;
import io.zeebe.engine.nwe.behavior.BpmnStateTransitionBehavior;
import io.zeebe.engine.nwe.behavior.BpmnWorkflowResultSenderBehavior;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowElementContainer;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;

public final class ProcessProcessor
    implements BpmnElementContainerProcessor<ExecutableFlowElementContainer> {

  private final BpmnStateBehavior stateBehavior;
  private final BpmnStateTransitionBehavior stateTransitionBehavior;
  private final BpmnEventSubscriptionBehavior eventSubscriptionBehavior;
  private final BpmnIncidentBehavior incidentBehavior;
  private final BpmnWorkflowResultSenderBehavior workflowResultSenderBehavior;
  private final BpmnBufferedMessageStartEventBehavior bufferedMessageStartEventBehavior;

  public ProcessProcessor(final BpmnBehaviors bpmnBehaviors) {
    stateBehavior = bpmnBehaviors.stateBehavior();
    stateTransitionBehavior = bpmnBehaviors.stateTransitionBehavior();
    eventSubscriptionBehavior = bpmnBehaviors.eventSubscriptionBehavior();
    incidentBehavior = bpmnBehaviors.incidentBehavior();
    workflowResultSenderBehavior = bpmnBehaviors.workflowResultSenderBehavior();
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

    final var parentWorkflowInstanceKey = context.getParentWorkflowInstanceKey();
    final var parentElementInstanceKey = context.getParentElementInstanceKey();

    if (parentWorkflowInstanceKey > 0) {
      // workflow instance is created by a call activity

      // TODO (saig0): move responsibility to call activity (#4473)
      final var parentElementInstance = stateBehavior.getParentElementInstanceContext(context);

      if (parentElementInstance != null
          && parentElementInstance.getIntent() == WorkflowInstanceIntent.ELEMENT_ACTIVATED) {
        // complete the corresponding call activity

        stateTransitionBehavior.transitionToCompleting(parentElementInstance);

        // propagate the variables to the parent
        final var variablesState = stateBehavior.getVariablesState();

        final var variables =
            variablesState.getVariablesAsDocument(context.getElementInstanceKey());
        variablesState.setTemporaryVariables(parentElementInstanceKey, variables);
      }
    }

    if (element.hasNoneStartEvent()) {
      workflowResultSenderBehavior.sendResult(context);
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

    stateTransitionBehavior.terminateChildInstances(context);
  }

  @Override
  public void onTerminated(
      final ExecutableFlowElementContainer element, final BpmnElementContext context) {

    incidentBehavior.resolveIncidents(context);

    final var parentWorkflowInstanceKey = context.getParentWorkflowInstanceKey();

    if (parentWorkflowInstanceKey > 0) {
      // workflow instance is created by a call activity

      // TODO (saig0): move responsibility to call activity (#4473)
      final var parentElementInstance = stateBehavior.getParentElementInstanceContext(context);

      if (parentElementInstance != null
          && parentElementInstance.getIntent() == WorkflowInstanceIntent.ELEMENT_TERMINATING) {
        // terminate the corresponding call activity

        stateTransitionBehavior.transitionToTerminated(parentElementInstance);
      }
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

    if (flowScopeContext.getIntent() == WorkflowInstanceIntent.ELEMENT_TERMINATING
        && stateBehavior.isLastActiveExecutionPathInScope(childContext)) {
      stateTransitionBehavior.transitionToTerminated(flowScopeContext);

    } else {
      eventSubscriptionBehavior.publishTriggeredEventSubProcess(flowScopeContext);
    }
  }
}
