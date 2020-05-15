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
import io.zeebe.engine.nwe.behavior.BpmnBehaviors;
import io.zeebe.engine.nwe.behavior.BpmnEventSubscriptionBehavior;
import io.zeebe.engine.nwe.behavior.BpmnStateBehavior;
import io.zeebe.engine.nwe.behavior.BpmnStateTransitionBehavior;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowElementContainer;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;

public final class SubProcessProcessor
    implements BpmnElementContainerProcessor<ExecutableFlowElementContainer> {

  private final BpmnStateBehavior stateBehavior;
  private final BpmnStateTransitionBehavior stateTransitionBehavior;
  private final BpmnEventSubscriptionBehavior eventSubscriptionBehavior;

  public SubProcessProcessor(final BpmnBehaviors bpmnBehaviors) {
    stateBehavior = bpmnBehaviors.stateBehavior();
    stateTransitionBehavior = bpmnBehaviors.stateTransitionBehavior();
    eventSubscriptionBehavior = bpmnBehaviors.eventSubscriptionBehavior();
  }

  @Override
  public void onChildCompleted(
      final ExecutableFlowElementContainer element,
      final BpmnElementContext flowScopeContext,
      final BpmnElementContext childContext) {

    // TODO (saig0): consume token before calling,
    //  this can be done when the rest of this processor is implemented
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

  @Override
  public Class<ExecutableFlowElementContainer> getType() {
    return ExecutableFlowElementContainer.class;
  }

  @Override
  public void onActivating(
      final ExecutableFlowElementContainer element, final BpmnElementContext context) {}

  @Override
  public void onActivated(
      final ExecutableFlowElementContainer element, final BpmnElementContext context) {}

  @Override
  public void onCompleting(
      final ExecutableFlowElementContainer element, final BpmnElementContext context) {}

  @Override
  public void onCompleted(
      final ExecutableFlowElementContainer element, final BpmnElementContext context) {}

  @Override
  public void onTerminating(
      final ExecutableFlowElementContainer element, final BpmnElementContext context) {}

  @Override
  public void onTerminated(
      final ExecutableFlowElementContainer element, final BpmnElementContext context) {}

  @Override
  public void onEventOccurred(
      final ExecutableFlowElementContainer element, final BpmnElementContext context) {}
}
