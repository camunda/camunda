/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.processing.bpmn.sequenceflow;

import io.zeebe.engine.processing.bpmn.BpmnElementContext;
import io.zeebe.engine.processing.bpmn.BpmnElementProcessor;
import io.zeebe.engine.processing.bpmn.BpmnProcessingException;
import io.zeebe.engine.processing.bpmn.behavior.BpmnBehaviors;
import io.zeebe.engine.processing.bpmn.behavior.BpmnDeferredRecordsBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnStateBehavior;
import io.zeebe.engine.processing.bpmn.behavior.BpmnStateTransitionBehavior;
import io.zeebe.engine.processing.deployment.model.element.ExecutableFlowNode;
import io.zeebe.engine.processing.deployment.model.element.ExecutableSequenceFlow;
import io.zeebe.engine.state.instance.IndexedRecord;
import io.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.zeebe.protocol.record.value.BpmnElementType;
import java.util.stream.Collectors;

/**
 * A sequence flow doesn't have a proper lifecycle as the other BPMN elements. It can only be taken.
 * However, it implements the same interface to keep the rest of implementation simple. But only the
 * method {@link #onActivating(ExecutableSequenceFlow, BpmnElementContext)} perform the action.
 * Calling other methods causes an exception.
 */
public final class SequenceFlowProcessor implements BpmnElementProcessor<ExecutableSequenceFlow> {

  private static final String UNSUPPORTED_OPERATION_MESSAGE =
      "This is not the method you're looking for.";

  private final BpmnStateTransitionBehavior stateTransitionBehavior;
  private final BpmnStateBehavior stateBehavior;
  private final BpmnDeferredRecordsBehavior deferredRecordsBehavior;

  public SequenceFlowProcessor(final BpmnBehaviors bpmnBehaviors) {
    stateTransitionBehavior = bpmnBehaviors.stateTransitionBehavior();
    stateBehavior = bpmnBehaviors.stateBehavior();
    deferredRecordsBehavior = bpmnBehaviors.deferredRecordsBehavior();
  }

  @Override
  public Class<ExecutableSequenceFlow> getType() {
    return ExecutableSequenceFlow.class;
  }

  @Override
  public void onActivating(final ExecutableSequenceFlow element, final BpmnElementContext context) {
    onSequenceFlowTaken(element, context);
  }

  @Override
  public void onActivated(final ExecutableSequenceFlow element, final BpmnElementContext context) {
    throw new BpmnProcessingException(context, UNSUPPORTED_OPERATION_MESSAGE);
  }

  @Override
  public void onCompleting(final ExecutableSequenceFlow element, final BpmnElementContext context) {
    throw new BpmnProcessingException(context, UNSUPPORTED_OPERATION_MESSAGE);
  }

  @Override
  public void onCompleted(final ExecutableSequenceFlow element, final BpmnElementContext context) {
    throw new BpmnProcessingException(context, UNSUPPORTED_OPERATION_MESSAGE);
  }

  @Override
  public void onTerminating(
      final ExecutableSequenceFlow element, final BpmnElementContext context) {
    throw new BpmnProcessingException(context, UNSUPPORTED_OPERATION_MESSAGE);
  }

  @Override
  public void onTerminated(final ExecutableSequenceFlow element, final BpmnElementContext context) {
    throw new BpmnProcessingException(context, UNSUPPORTED_OPERATION_MESSAGE);
  }

  @Override
  public void onEventOccurred(
      final ExecutableSequenceFlow element, final BpmnElementContext context) {
    throw new BpmnProcessingException(context, UNSUPPORTED_OPERATION_MESSAGE);
  }

  private void onSequenceFlowTaken(
      final ExecutableSequenceFlow element, final BpmnElementContext context) {

    final var targetElement = element.getTarget();

    if (targetElement.getElementType() == BpmnElementType.PARALLEL_GATEWAY) {
      joinOnParallelGateway(targetElement, context);

    } else {
      stateTransitionBehavior.activateElementInstanceInFlowScope(context, targetElement);
    }
  }

  private void joinOnParallelGateway(
      final ExecutableFlowNode parallelGateway, final BpmnElementContext context) {

    // before the parallel gateway is activated, each incoming sequence flow of the gateway must be
    // taken (at least once)

    // if a sequence flow is taken more than once then the redundant token remains for the next
    // activation of the gateway (Tetris principle)

    final var flowScopeContext = stateBehavior.getFlowScopeContext(context);

    // store which sequence flows are taken as deferred records
    deferredRecordsBehavior.deferNewRecord(
        flowScopeContext,
        context.getElementInstanceKey(),
        context.getRecordValue(),
        context.getIntent());

    final var tokensBySequenceFlow =
        deferredRecordsBehavior.getDeferredRecords(flowScopeContext).stream()
            .filter(record -> record.getState() == ProcessInstanceIntent.SEQUENCE_FLOW_TAKEN)
            .filter(record -> isIncomingSequenceFlow(record, parallelGateway))
            .collect(Collectors.groupingBy(record -> record.getValue().getElementIdBuffer()));

    if (tokensBySequenceFlow.size() == parallelGateway.getIncoming().size()) {
      // all incoming sequence flows are taken, so the gateway can be activated

      final var flowScopeInstance = stateBehavior.getFlowScopeInstance(context);

      // consume one token per sequence flow
      tokensBySequenceFlow.forEach(
          (sequenceFlow, tokens) -> {
            final var firstToken = tokens.get(0);
            deferredRecordsBehavior.removeDeferredRecord(flowScopeContext, firstToken);

            flowScopeInstance.consumeToken();
          });

      // spawn a new token for the activated gateway
      flowScopeInstance.spawnToken();
      stateBehavior.updateElementInstance(flowScopeInstance);

      stateTransitionBehavior.activateElementInstanceInFlowScope(context, parallelGateway);
    }
  }

  private boolean isIncomingSequenceFlow(
      final IndexedRecord record, final ExecutableFlowNode parallelGateway) {
    final var elementId = record.getValue().getElementIdBuffer();

    for (final ExecutableSequenceFlow incomingSequenceFlow : parallelGateway.getIncoming()) {
      if (elementId.equals(incomingSequenceFlow.getId())) {
        return true;
      }
    }
    return false;
  }
}
