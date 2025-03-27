/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.bpmn.gateway;

import io.camunda.zeebe.el.Expression;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.camunda.zeebe.engine.processing.bpmn.BpmnElementProcessor;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.camunda.zeebe.engine.processing.bpmn.behavior.BpmnJobBehavior;
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
  private final BpmnJobBehavior jobBehavior;

  public ExclusiveGatewayProcessor(
      final BpmnBehaviors behaviors, final BpmnStateTransitionBehavior stateTransitionBehavior) {
    this.stateTransitionBehavior = stateTransitionBehavior;
    expressionBehavior = behaviors.expressionBehavior();
    incidentBehavior = behaviors.incidentBehavior();
    jobBehavior = behaviors.jobBehavior();
  }

  @Override
  public Class<ExecutableExclusiveGateway> getType() {
    return ExecutableExclusiveGateway.class;
  }

  @Override
  public Either<Failure, ?> finalizeActivation(
      final ExecutableExclusiveGateway element, final BpmnElementContext activating) {
    // find outgoing sequence flow with fulfilled condition or the default (or none if implicit end)
    return findSequenceFlowToTake(element, activating)
        .flatMap(
            optFlow -> {
              final var activated =
                  stateTransitionBehavior.transitionToActivated(activating, element.getEventType());
              final var completing = stateTransitionBehavior.transitionToCompleting(activated);
              return stateTransitionBehavior
                  .transitionToCompleted(element, completing)
                  .thenDo(
                      completed -> {
                        optFlow.ifPresent(
                            flow -> stateTransitionBehavior.takeSequenceFlow(completed, flow));
                      });
            });
  }

  @Override
  public Either<Failure, ?> finalizeCompletion(
      final ExecutableExclusiveGateway element, final BpmnElementContext context) {
    throw new UnsupportedOperationException(
        String.format(
            "Expected to explicitly process complete, but gateway %s has no wait state",
            BufferUtil.bufferAsString(context.getElementId())));
  }

  @Override
  public TransitionOutcome onTerminate(
      final ExecutableExclusiveGateway element, final BpmnElementContext context) {
    if (element.hasExecutionListeners()) {
      jobBehavior.cancelJob(context);
    }

    incidentBehavior.resolveIncidents(context);
    final var terminated =
        stateTransitionBehavior.transitionToTerminated(context, element.getEventType());
    stateTransitionBehavior.onElementTerminated(element, terminated);
    return TransitionOutcome.CONTINUE;
  }

  private Either<Failure, Optional<ExecutableSequenceFlow>> findSequenceFlowToTake(
      final ExecutableExclusiveGateway element, final BpmnElementContext context) {

    if (element.getOutgoing().isEmpty()) {
      // there are no flows to take: the gateway is an implicit end for the flow scope
      return Either.right(Optional.empty());
    }

    final var outgoingSequenceFlows = element.getOutgoing();
    if (outgoingSequenceFlows.size() == 1) {
      final var singleOutgoingSequenceFlow = outgoingSequenceFlows.getFirst();
      if (singleOutgoingSequenceFlow.getCondition() == null) {
        // only one flow without a condition, can just be taken
        return Either.right(Optional.of(singleOutgoingSequenceFlow));
      }
    }

    for (final ExecutableSequenceFlow sequenceFlow : element.getOutgoingWithCondition()) {
      if (element.getDefaultFlow() == null || element.getDefaultFlow() != sequenceFlow) {
        final Expression condition = sequenceFlow.getCondition();
        final Either<Failure, Boolean> isFulfilledOrFailure =
            expressionBehavior.evaluateBooleanExpression(
                condition, context.getElementInstanceKey());
        if (isFulfilledOrFailure.isLeft()) {
          return Either.left(isFulfilledOrFailure.getLeft());

        } else if (isFulfilledOrFailure.get()) {
          // the condition is fulfilled
          return Either.right(Optional.of(sequenceFlow));
        }
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
