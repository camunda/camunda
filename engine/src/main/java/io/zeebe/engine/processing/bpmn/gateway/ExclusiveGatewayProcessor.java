/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processing.bpmn.gateway;

import static io.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.zeebe.el.Expression;
import io.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.zeebe.engine.processing.bpmn.BpmnElementProcessor;
import io.zeebe.engine.processing.bpmn.BpmnProcessingException;
import io.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.zeebe.engine.processing.bpmn.behavior.BpmnDeferredRecordsBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnIncidentBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.zeebe.engine.processing.common.ExpressionProcessor;
import io.zeebe.engine.processing.common.Failure;
import io.zeebe.engine.processing.deployment.model.element.ExecutableExclusiveGateway;
import io.zeebe.engine.processing.deployment.model.element.ExecutableSequenceFlow;
import io.zeebe.engine.state.instance.IndexedRecord;
import io.zeebe.protocol.impl.record.value.workflowinstance.WorkflowInstanceRecord;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import io.zeebe.protocol.record.value.ErrorType;
import io.zeebe.util.Either;

public final class ExclusiveGatewayProcessor
    implements BpmnElementProcessor<ExecutableExclusiveGateway> {

  private static final String NO_OUTGOING_FLOW_CHOSEN_ERROR =
      "Expected at least one condition to evaluate to true, or to have a default flow";

  private final WorkflowInstanceRecord record = new WorkflowInstanceRecord();

  private final BpmnStateBehavior stateBehavior;
  private final BpmnStateTransitionBehavior stateTransitionBehavior;
  private final BpmnIncidentBehavior incidentBehavior;
  private final BpmnDeferredRecordsBehavior deferredRecordsBehavior;
  private final ExpressionProcessor expressionBehavior;

  public ExclusiveGatewayProcessor(final BpmnBehaviors behaviors) {
    expressionBehavior = behaviors.expressionBehavior();
    incidentBehavior = behaviors.incidentBehavior();
    stateBehavior = behaviors.stateBehavior();
    deferredRecordsBehavior = behaviors.deferredRecordsBehavior();
    stateTransitionBehavior = behaviors.stateTransitionBehavior();
  }

  @Override
  public Class<ExecutableExclusiveGateway> getType() {
    return ExecutableExclusiveGateway.class;
  }

  @Override
  public void onActivate(
      final ExecutableExclusiveGateway element, final BpmnElementContext context) {
    if (element.getOutgoing().isEmpty()) {
      // there are no flows to take: the gateway is an implicit end for the flow scope
      final var activatedContext = stateTransitionBehavior.transitionToActivated(context);
      // todo (@korthout): write COMPLETE_ELEMENT command
      stateTransitionBehavior.transitionToCompleting(activatedContext);
    } else {
      // find outgoing sequence flow with fulfilled condition or the default
      findSequenceFlowToTake(element, context)
          .ifRightOrLeft(
              sequenceFlow -> {
                final var activatedContext = stateTransitionBehavior.transitionToActivated(context);

                // todo (@korthout): this should be done differently... we can't change state
                // without a record
                // defer sequence flow taken, as it will only be taken when the gateway is completed
                record.wrap(context.getRecordValue());
                record.setElementId(sequenceFlow.getId());
                record.setBpmnElementType(BpmnElementType.SEQUENCE_FLOW);
                deferredRecordsBehavior.deferNewRecord(
                    context,
                    context.getElementInstanceKey(),
                    record,
                    WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN);

                // todo (@korthout): write COMPLETE_ELEMENT command
                stateTransitionBehavior.transitionToCompleting(activatedContext);
              },
              failure -> incidentBehavior.createIncident(failure, context));
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
  public void onCompleting(
      final ExecutableExclusiveGateway element, final BpmnElementContext context) {
    stateTransitionBehavior.transitionToCompleted(context);
  }

  @Override
  public void onCompleted(
      final ExecutableExclusiveGateway element, final BpmnElementContext context) {

    deferredRecordsBehavior.getDeferredRecords(context).stream()
        .filter(r -> r.hasState(WorkflowInstanceIntent.SEQUENCE_FLOW_TAKEN))
        .findFirst()
        .map(r -> getOutgoingSequenceFlow(element, context, r))
        .ifPresentOrElse(
            sequenceFlow -> stateTransitionBehavior.takeSequenceFlow(context, sequenceFlow),
            () -> stateTransitionBehavior.onElementCompleted(element, context));

    stateBehavior.consumeToken(context);
    stateBehavior.removeElementInstance(context);
  }

  @Override
  public void onTerminating(
      final ExecutableExclusiveGateway element, final BpmnElementContext context) {
    stateTransitionBehavior.transitionToTerminated(context);
  }

  @Override
  public void onTerminated(
      final ExecutableExclusiveGateway element, final BpmnElementContext context) {

    incidentBehavior.resolveIncidents(context);

    stateTransitionBehavior.onElementTerminated(element, context);

    stateBehavior.consumeToken(context);
    stateBehavior.removeElementInstance(context);
  }

  @Override
  public void onEventOccurred(
      final ExecutableExclusiveGateway element, final BpmnElementContext context) {
    throw new BpmnProcessingException(
        context,
        "Expected to handle occurred event on exclusive gateway, but events should not occur on exclusive gateway.");
  }

  private ExecutableSequenceFlow getOutgoingSequenceFlow(
      final ExecutableExclusiveGateway element,
      final BpmnElementContext context,
      final IndexedRecord record) {

    final var sequenceFlowId = record.getValue().getElementIdBuffer();

    return element.getOutgoing().stream()
        .filter(sequenceFlow -> sequenceFlow.getId().equals(sequenceFlowId))
        .findFirst()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    String.format(
                        "Expected sequence flow with id '%s' but not found. [context: %s]",
                        bufferAsString(sequenceFlowId), context)));
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
