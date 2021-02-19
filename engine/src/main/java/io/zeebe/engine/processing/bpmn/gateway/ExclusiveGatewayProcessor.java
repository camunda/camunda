/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.bpmn.gateway;

import io.zeebe.el.Expression;
import io.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.zeebe.engine.processing.bpmn.BpmnElementProcessor;
import io.zeebe.engine.processing.bpmn.BpmnProcessingException;
import io.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.zeebe.engine.processing.common.ExpressionProcessor;
import io.zeebe.engine.processing.common.Failure;
import io.zeebe.engine.processing.deployment.model.element.ExecutableExclusiveGateway;
import io.zeebe.engine.processing.deployment.model.element.ExecutableSequenceFlow;
import io.zeebe.protocol.record.value.ErrorType;
import io.zeebe.util.Either;
import io.zeebe.util.buffer.BufferUtil;

public final class ExclusiveGatewayProcessor
    implements BpmnElementProcessor<ExecutableExclusiveGateway> {

  private static final String NO_OUTGOING_FLOW_CHOSEN_ERROR =
      "Expected at least one condition to evaluate to true, or to have a default flow";

  private final BpmnStateBehavior stateBehavior;
  private final BpmnStateTransitionBehavior stateTransitionBehavior;
  private final BpmnIncidentBehavior incidentBehavior;
  private final ExpressionProcessor expressionBehavior;

  public ExclusiveGatewayProcessor(final BpmnBehaviors behaviors) {
    expressionBehavior = behaviors.expressionBehavior();
    incidentBehavior = behaviors.incidentBehavior();
    stateBehavior = behaviors.stateBehavior();
    stateTransitionBehavior = behaviors.stateTransitionBehavior();
  }

  @Override
  public Class<ExecutableExclusiveGateway> getType() {
    return ExecutableExclusiveGateway.class;
  }

  @Override
  public void onActivate(
      final ExecutableExclusiveGateway element, final BpmnElementContext activating) {
    if (element.getOutgoing().isEmpty()) {
      // there are no flows to take: the gateway is an implicit end for the flow scope
      final var activated = stateTransitionBehavior.transitionToActivated(activating);
      final var completing = stateTransitionBehavior.transitionToCompleting(activated);
      final var completed = stateTransitionBehavior.transitionToCompleted(completing);
      stateTransitionBehavior.onElementCompleted(element, completed);

    } else {
      // find outgoing sequence flow with fulfilled condition or the default
      findSequenceFlowToTake(element, activating)
          .ifRightOrLeft(
              sequenceFlow -> {
                final var activated = stateTransitionBehavior.transitionToActivated(activating);
                final var completing = stateTransitionBehavior.transitionToCompleting(activated);
                final var completed = stateTransitionBehavior.transitionToCompleted(completing);
                stateTransitionBehavior.takeSequenceFlow(completed, sequenceFlow);
              },
              failure -> incidentBehavior.createIncident(failure, activating));
    }
  }

  @Override
  public void onActivating(
      final ExecutableExclusiveGateway element, final BpmnElementContext context) {
    throw new UnsupportedOperationException("This method is replaced by onActivate");
  }

  @Override
  public void onActivated(
      final ExecutableExclusiveGateway element, final BpmnElementContext context) {
    throw new UnsupportedOperationException("This method is replaced by onActivate");
  }

  @Override
  public void onComplete(
      final ExecutableExclusiveGateway element, final BpmnElementContext context) {
    throw new UnsupportedOperationException(
        String.format(
            "Expected to explicitly process complete, but gateway %s has no wait state",
            BufferUtil.bufferAsString(context.getElementId())));
  }

  @Override
  public void onCompleting(
      final ExecutableExclusiveGateway element, final BpmnElementContext context) {
    throw new UnsupportedOperationException("This method is replaced by onActivate");
  }

  @Override
  public void onCompleted(
      final ExecutableExclusiveGateway element, final BpmnElementContext context) {
    throw new UnsupportedOperationException("This method is replaced by onActivate");
  }

  @Override
  public void onTerminate(
      final ExecutableExclusiveGateway element, final BpmnElementContext context) {
    incidentBehavior.resolveIncidents(context);
    final var terminated = stateTransitionBehavior.transitionToTerminated(context);
    stateTransitionBehavior.onElementTerminated(element, terminated);
  }

  @Override
  public void onTerminating(
      final ExecutableExclusiveGateway element, final BpmnElementContext context) {
    throw new UnsupportedOperationException("This method is replaced by onTerminate");
  }

  @Override
  public void onTerminated(
      final ExecutableExclusiveGateway element, final BpmnElementContext context) {
    throw new UnsupportedOperationException("This method is replaced by onTerminate");
  }

  @Override
  public void onEventOccurred(
      final ExecutableExclusiveGateway element, final BpmnElementContext context) {
    throw new BpmnProcessingException(
        context,
        "Expected to handle occurred event on exclusive gateway, but events should not occur on exclusive gateway.");
  }

  private Either<Failure, ExecutableSequenceFlow> findSequenceFlowToTake(
      final ExecutableExclusiveGateway element, final BpmnElementContext context) {

    if (element.getOutgoing().size() == 1 && element.getOutgoing().get(0).getCondition() == null) {
      // only one flow without a condition, can just be taken
      return Either.right(element.getOutgoing().get(0));
    }

    for (final ExecutableSequenceFlow sequenceFlow : element.getOutgoingWithCondition()) {
      final Expression condition = sequenceFlow.getCondition();
      final Either<Failure, Boolean> isFulfilledOrFailure =
          expressionBehavior.evaluateBooleanExpression(condition, context.getElementInstanceKey());
      if (isFulfilledOrFailure.isLeft()) {
        return Either.left(isFulfilledOrFailure.getLeft());

      } else if (isFulfilledOrFailure.get()) {
        // the condition is fulfilled
        return Either.right(sequenceFlow);
      }
    }

    // no condition is fulfilled - try to take the default flow
    if (element.getDefaultFlow() != null) {
      return Either.right(element.getDefaultFlow());
    }
    return Either.left(
        new Failure(
            NO_OUTGOING_FLOW_CHOSEN_ERROR,
            ErrorType.CONDITION_ERROR,
            context.getElementInstanceKey()));
  }
}
