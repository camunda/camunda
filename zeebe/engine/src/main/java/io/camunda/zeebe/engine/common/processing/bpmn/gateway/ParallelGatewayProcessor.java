/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.processing.bpmn.gateway;

import io.camunda.zeebe.engine.common.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.common.processing.bpmn.BpmnElementProcessor;
import io.camunda.zeebe.engine.common.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.common.processing.bpmn.behavior.BpmnJobBehavior;
import io.camunda.zeebe.engine.common.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.camunda.zeebe.engine.common.processing.common.Failure;
import io.camunda.zeebe.engine.common.processing.deployment.model.element.ExecutableFlowNode;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;

public final class ParallelGatewayProcessor implements BpmnElementProcessor<ExecutableFlowNode> {

  private final BpmnStateTransitionBehavior stateTransitionBehavior;
  private final BpmnJobBehavior jobBehavior;

  public ParallelGatewayProcessor(
      final BpmnBehaviors behaviors, final BpmnStateTransitionBehavior stateTransitionBehavior) {
    this.stateTransitionBehavior = stateTransitionBehavior;
    jobBehavior = behaviors.jobBehavior();
  }

  @Override
  public Class<ExecutableFlowNode> getType() {
    return ExecutableFlowNode.class;
  }

  @Override
  public Either<Failure, ?> finalizeActivation(
      final ExecutableFlowNode element, final BpmnElementContext context) {
    // the joining of the incoming sequence flows into the parallel gateway happens in the
    // sequence flow processor. The activating event of the parallel gateway is written when all
    // incoming sequence flows are taken
    final var activated =
        stateTransitionBehavior.transitionToActivated(context, element.getEventType());
    final var completing = stateTransitionBehavior.transitionToCompleting(activated);
    return stateTransitionBehavior
        .transitionToCompleted(element, completing)
        .thenDo(
            completed ->
                stateTransitionBehavior
                    .executeRuntimeInstructions(element, completed)
                    // fork the process processing by taking all outgoing sequence flows of the
                    // parallel
                    // gateway
                    .ifRight(
                        notInterrupted ->
                            stateTransitionBehavior.takeOutgoingSequenceFlows(element, completed)));
  }

  @Override
  public Either<Failure, ?> finalizeCompletion(
      final ExecutableFlowNode element, final BpmnElementContext context) {
    throw new UnsupportedOperationException(
        String.format(
            "Expected to explicitly process complete, but gateway %s has already been completed on processing activate",
            BufferUtil.bufferAsString(context.getElementId())));
  }

  @Override
  public TransitionOutcome onTerminate(
      final ExecutableFlowNode element, final BpmnElementContext context) {
    if (element.hasExecutionListeners()) {
      jobBehavior.cancelJob(context);
    }

    final var terminated =
        stateTransitionBehavior.transitionToTerminated(context, element.getEventType());
    stateTransitionBehavior.onElementTerminated(element, terminated);
    return TransitionOutcome.CONTINUE;
  }

  @Override
  public void finalizeTermination(
      final ExecutableFlowNode element, final BpmnElementContext context) {
    stateTransitionBehavior.executeRuntimeInstructions(element, context);
  }
}
