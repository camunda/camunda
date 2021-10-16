/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.processing.bpmn.gateway;

import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementProcessor;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.camunda.zeebe.engine.processing.common.ExpressionProcessor;
import io.camunda.zeebe.engine.processing.common.Failure;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableExclusiveGateway;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableSequenceFlow;
import io.camunda.zeebe.protocol.record.value.ErrorType;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Optional;

public final class ExclusiveGatewayProcessor
    implements BpmnElementProcessor<ExecutableExclusiveGateway> {

  private static final String NO_OUTGOING_FLOW_CHOSEN_ERROR =
      "Expected at least one condition to evaluate to true, or to have a default flow";

  private final BpmnStateTransitionBehavior stateTransitionBehavior;
  private final BpmnIncidentBehavior incidentBehavior;
  private final ExpressionProcessor expressionBehavior;

  public ExclusiveGatewayProcessor(final BpmnBehaviors behaviors) {
    expressionBehavior = behaviors.expressionBehavior();
    incidentBehavior = behaviors.incidentBehavior();
    stateTransitionBehavior = behaviors.stateTransitionBehavior();
  }

  @Override
  public Class<ExecutableExclusiveGateway> getType() {
    return ExecutableExclusiveGateway.class;
  }

  @Override
  public void onActivate(
      final ExecutableExclusiveGateway element, final BpmnElementContext activating) {
    // find outgoing sequence flow with fulfilled condition or the default (or none if implicit end)
    findSequenceFlowToTake(element, activating)
        .ifRightOrLeft(
            optFlow -> {
              final var activated = stateTransitionBehavior.transitionToActivated(activating);
              final var completing = stateTransitionBehavior.transitionToCompleting(activated);
              stateTransitionBehavior
                  .transitionToCompleted(element, completing)
                  .ifRightOrLeft(
                      completed ->
                          optFlow.ifPresent(
                              flow -> stateTransitionBehavior.takeSequenceFlow(completed, flow)),
                      failure -> incidentBehavior.createIncident(failure, completing));
            },
            failure -> incidentBehavior.createIncident(failure, activating));
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
  public void onTerminate(
      final ExecutableExclusiveGateway element, final BpmnElementContext context) {
    incidentBehavior.resolveIncidents(context);
    final var terminated = stateTransitionBehavior.transitionToTerminated(context);
    stateTransitionBehavior.onElementTerminated(element, terminated);
  }

  private Either<Failure, Optional<ExecutableSequenceFlow>> findSequenceFlowToTake(
      final ExecutableExclusiveGateway element, final BpmnElementContext context) {

    if (element.getOutgoing().isEmpty()) {
      // there are no flows to take: the gateway is an implicit end for the flow scope
      return Either.right(Optional.empty());
    }

    if (element.getOutgoing().size() == 1 && element.getOutgoing().get(0).getCondition() == null) {
      // only one flow without a condition, can just be taken
      return Either.right(Optional.of(element.getOutgoing().get(0)));
    }

    for (final ExecutableSequenceFlow sequenceFlow : element.getOutgoingWithCondition()) {
      final Expression condition = sequenceFlow.getCondition();
      final Either<Failure, Boolean> isFulfilledOrFailure =
          expressionBehavior.evaluateBooleanExpression(condition, context.getElementInstanceKey());
      if (isFulfilledOrFailure.isLeft()) {
        return Either.left(isFulfilledOrFailure.getLeft());

      } else if (isFulfilledOrFailure.get()) {
        // the condition is fulfilled
        return Either.right(Optional.of(sequenceFlow));
      }
    }

    // no condition is fulfilled - try to take the default flow
    if (element.getDefaultFlow() != null) {
      return Either.right(Optional.of(element.getDefaultFlow()));
    }

    return Either.left(
        new Failure(
            NO_OUTGOING_FLOW_CHOSEN_ERROR,
            ErrorType.CONDITION_ERROR,
            context.getElementInstanceKey()));
  }
}
