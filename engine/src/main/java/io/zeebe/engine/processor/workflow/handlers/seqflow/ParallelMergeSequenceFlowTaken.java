/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.engine.processor.workflow.handlers.seqflow;

import io.zeebe.engine.processor.workflow.BpmnStepContext;
import io.zeebe.engine.processor.workflow.EventOutput;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableFlowNode;
import io.zeebe.engine.processor.workflow.deployment.model.element.ExecutableSequenceFlow;
import io.zeebe.engine.processor.workflow.handlers.AbstractHandler;
import io.zeebe.engine.state.instance.ElementInstance;
import io.zeebe.engine.state.instance.IndexedRecord;
import io.zeebe.protocol.record.intent.WorkflowInstanceIntent;
import java.util.ArrayList;
import java.util.List;

public class ParallelMergeSequenceFlowTaken<T extends ExecutableSequenceFlow>
    extends AbstractHandler<T> {
  public ParallelMergeSequenceFlowTaken() {
    super(null);
  }

  @Override
  protected boolean handleState(BpmnStepContext<T> context) {
    final ElementInstance scopeInstance = context.getFlowScopeInstance();
    final EventOutput eventOutput = context.getOutput();
    final ExecutableSequenceFlow sequenceFlow = context.getElement();
    final ExecutableFlowNode gateway = sequenceFlow.getTarget();

    eventOutput.deferEvent(context.getState(), context.getValue());

    final List<IndexedRecord> mergeableRecords =
        getMergeableRecords(context, gateway, scopeInstance);
    if (mergeableRecords.size() == gateway.getIncoming().size()) {

      // consume all deferred tokens and spawn a new one to continue after the gateway
      mergeableRecords.forEach(
          r -> {
            eventOutput.removeDeferredEvent(scopeInstance.getKey(), r.getKey());
            scopeInstance.consumeToken();
          });

      context
          .getOutput()
          .appendNewEvent(WorkflowInstanceIntent.ELEMENT_ACTIVATING, context.getValue(), gateway);
      scopeInstance.spawnToken();
      context.getStateDb().getElementInstanceState().updateInstance(scopeInstance);
    }

    return true;
  }

  @Override
  protected boolean shouldHandleState(BpmnStepContext<T> context) {
    return super.shouldHandleState(context) && isElementActive(context.getFlowScopeInstance());
  }

  /** @return the records that can be merged */
  private List<IndexedRecord> getMergeableRecords(
      BpmnStepContext<T> context,
      ExecutableFlowNode parallelGateway,
      ElementInstance scopeInstance) {
    final List<ExecutableSequenceFlow> incomingFlows = parallelGateway.getIncoming();
    final List<IndexedRecord> mergingRecords = new ArrayList<>(incomingFlows.size());

    final List<IndexedRecord> storedRecords =
        context.getElementInstanceState().getDeferredRecords(scopeInstance.getKey());

    for (final ExecutableSequenceFlow flow : incomingFlows) {
      for (final IndexedRecord recordToMatch : storedRecords) {
        if (recordToMatch.getValue().getElementIdBuffer().equals(flow.getId())) {
          mergingRecords.add(recordToMatch);
          break;
        }
      }
    }

    return mergingRecords;
  }
}
