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
import io.zeebe.engine.processing.streamprocessor.MigratedStreamProcessors;
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

    final var parentProcessInstanceKey = context.getParentProcessInstanceKey();
    BpmnElementContext parentElementInstanceContext = null;
    if (parentProcessInstanceKey > 0) {
      // needs to be done before we delete the Process element instance
      parentElementInstanceContext = stateBehavior.getParentElementInstanceContext(context);
    }

    // we need to send the result before we transition to completed, since the
    // event applier will delete the element instance
    processResultSenderBehavior.sendResult(context);

    final var completed = stateTransitionBehavior.transitionToCompleted(context);

    if (parentElementInstanceContext != null) {
      stateTransitionBehavior.onCalledProcessCompleted(completed, parentElementInstanceContext);
    }

    if (element.hasMessageStartEvent()) {
      bufferedMessageStartEventBehavior.correlateMessage(completed);
    }
  }

  @Override
  public void onTerminate(
      final ExecutableFlowElementContainer element, final BpmnElementContext context) {
    eventSubscriptionBehavior.unsubscribeFromEvents(context);

    final var noActiveChildInstances = stateTransitionBehavior.terminateChildInstances(context);

    if (noActiveChildInstances) {

      final var parentProcessInstanceKey = context.getParentProcessInstanceKey();
      BpmnElementContext parentElementInstanceContext = null;
      if (parentProcessInstanceKey > 0) {
        // needs to be done before we delete the Process element instance
        parentElementInstanceContext = stateBehavior.getParentElementInstanceContext(context);
      }

      final var terminated = stateTransitionBehavior.transitionToTerminated(context);
      incidentBehavior.resolveIncidents(terminated);

      if (parentElementInstanceContext != null) {
        stateTransitionBehavior.onCalledProcessTerminated(terminated, parentElementInstanceContext);
      }
    } else {
      incidentBehavior.resolveIncidents(context);
    }

    if (element.hasMessageStartEvent()) {
      bufferedMessageStartEventBehavior.correlateMessage(context);
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
  public void onChildActivating(
      final ExecutableFlowElementContainer element,
      final BpmnElementContext flowScopeContext,
      final BpmnElementContext childContext) {}

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

    if (flowScopeContext.getIntent() == ProcessInstanceIntent.ELEMENT_TERMINATING
        && stateBehavior.canBeTerminated(childContext)) {

      final var parentProcessInstanceKey = flowScopeContext.getParentProcessInstanceKey();
      BpmnElementContext parentElementInstanceContext = null;
      if (parentProcessInstanceKey > 0) {
        // needs to be done before we delete the Process element instance
        parentElementInstanceContext =
            stateBehavior.getParentElementInstanceContext(flowScopeContext);
      }

      // the event appliers will remove the process instance, this is the reason
      // why we need to extract the parent element instance before the reference is deleted
      stateTransitionBehavior.transitionToTerminated(flowScopeContext);

      if (parentElementInstanceContext != null) {
        stateTransitionBehavior.onCalledProcessTerminated(
            flowScopeContext, parentElementInstanceContext);
      }
    } else {
      eventSubscriptionBehavior.publishTriggeredEventSubProcess(
          MigratedStreamProcessors.isMigrated(childContext.getBpmnElementType()), flowScopeContext);
    }
  }
}
