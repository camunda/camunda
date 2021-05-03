/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.bpmn.gateway;

import io.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.zeebe.engine.processing.bpmn.BpmnElementProcessor;
import io.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.zeebe.engine.processing.deployment.model.element.ExecutableFlowNode;
import io.zeebe.util.buffer.BufferUtil;

public final class ParallelGatewayProcessor implements BpmnElementProcessor<ExecutableFlowNode> {

  private final BpmnStateTransitionBehavior stateTransitionBehavior;

  public ParallelGatewayProcessor(final BpmnBehaviors behaviors) {
    stateTransitionBehavior = behaviors.stateTransitionBehavior();
  }

  @Override
  public Class<ExecutableFlowNode> getType() {
    return ExecutableFlowNode.class;
  }

  @Override
  public void onActivate(final ExecutableFlowNode element, final BpmnElementContext context) {
    // the joining of the incoming sequence flows into the parallel gateway happens in the
    // sequence flow processor. The activating event of the parallel gateway is written when all
    // incoming sequence flows are taken
    final var activated = stateTransitionBehavior.transitionToActivated(context);
    final var completing = stateTransitionBehavior.transitionToCompleting(activated);
    final var completed =
        stateTransitionBehavior.transitionToCompletedWithParentNotification(element, completing);
    // fork the process processing by taking all outgoing sequence flows of the parallel gateway
    stateTransitionBehavior.takeOutgoingSequenceFlows(element, completed);
  }

  @Override
  public void onComplete(final ExecutableFlowNode element, final BpmnElementContext context) {
    throw new UnsupportedOperationException(
        String.format(
            "Expected to explicitly process complete, but gateway %s has already been completed on processing activate",
            BufferUtil.bufferAsString(context.getElementId())));
  }

  @Override
  public void onTerminate(final ExecutableFlowNode element, final BpmnElementContext context) {
    final var terminated = stateTransitionBehavior.transitionToTerminated(context);
    stateTransitionBehavior.onElementTerminated(element, terminated);
  }
}
