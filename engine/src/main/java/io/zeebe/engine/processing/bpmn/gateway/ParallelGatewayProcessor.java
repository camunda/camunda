/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.bpmn.gateway;

import io.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.zeebe.engine.processing.bpmn.BpmnElementProcessor;
import io.zeebe.engine.processing.bpmn.BpmnProcessingException;
import io.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.zeebe.engine.processing.deployment.model.element.ExecutableFlowNode;

public final class ParallelGatewayProcessor implements BpmnElementProcessor<ExecutableFlowNode> {

  private final BpmnStateBehavior stateBehavior;
  private final BpmnStateTransitionBehavior stateTransitionBehavior;

  public ParallelGatewayProcessor(final BpmnBehaviors behaviors) {
    stateBehavior = behaviors.stateBehavior();
    stateTransitionBehavior = behaviors.stateTransitionBehavior();
  }

  @Override
  public Class<ExecutableFlowNode> getType() {
    return ExecutableFlowNode.class;
  }

  @Override
  public void onActivating(final ExecutableFlowNode element, final BpmnElementContext context) {
    // the joining of the incoming sequence flows into the parallel gateway happens in the
    // sequence flow processor

    // the activating event of the parallel gateway is written when all incoming sequence flows are
    // taken

    stateTransitionBehavior.transitionToActivated(context);
  }

  @Override
  public void onActivated(final ExecutableFlowNode element, final BpmnElementContext context) {

    stateTransitionBehavior.transitionToCompleting(context);
  }

  @Override
  public void onCompleting(final ExecutableFlowNode element, final BpmnElementContext context) {

    stateTransitionBehavior.transitionToCompleted(context);
  }

  @Override
  public void onCompleted(final ExecutableFlowNode element, final BpmnElementContext context) {
    // fork the workflow processing by taking all outgoing sequence flows of the parallel gateway

    stateTransitionBehavior.takeOutgoingSequenceFlows(element, context);

    stateBehavior.consumeToken(context);
    stateBehavior.removeElementInstance(context);
  }

  @Override
  public void onTerminating(final ExecutableFlowNode element, final BpmnElementContext context) {

    stateTransitionBehavior.transitionToTerminated(context);
  }

  @Override
  public void onTerminated(final ExecutableFlowNode element, final BpmnElementContext context) {

    stateTransitionBehavior.onElementTerminated(element, context);

    stateBehavior.consumeToken(context);
    stateBehavior.removeElementInstance(context);
  }

  @Override
  public void onEventOccurred(final ExecutableFlowNode element, final BpmnElementContext context) {
    throw new BpmnProcessingException(
        context,
        "Expected to handle occurred event on a parallel gateway, but events should not occur on a parallel gateway.");
  }
}
