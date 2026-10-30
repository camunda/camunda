/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableSequenceFlow;
import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableProcessState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;

/**
 * Reverse of ProcessInstanceSequenceFlowTakenApplier. Currently only used to migrate taken sequence
 * flows. We apply this intent and right after create a new sequence flow on the target process
 * definition.
 */
final class ProcessInstanceSequenceFlowDeletedApplier
    implements TypedEventApplier<ProcessInstanceIntent, ProcessInstanceRecord> {

  private final MutableElementInstanceState elementInstanceState;
  private final MutableProcessState processState;

  public ProcessInstanceSequenceFlowDeletedApplier(
      final MutableElementInstanceState elementInstanceState,
      final MutableProcessState processState) {
    this.elementInstanceState = elementInstanceState;
    this.processState = processState;
  }

  @Override
  public void applyState(final long key, final ProcessInstanceRecord value) {
    final var sequenceFlow =
        processState.getFlowElement(
            value.getProcessDefinitionKey(),
            value.getTenantId(),
            value.getElementIdBuffer(),
            ExecutableSequenceFlow.class);

    final var flowScopeInstance = elementInstanceState.getInstance(value.getFlowScopeKey());
    flowScopeInstance.decrementActiveSequenceFlows();
    flowScopeInstance.removeActiveSequenceFlowId(sequenceFlow.getId());
    elementInstanceState.updateInstance(flowScopeInstance);

    final var target = sequenceFlow.getTarget();
    if (target.getElementType() == BpmnElementType.PARALLEL_GATEWAY
        || target.getElementType() == BpmnElementType.INCLUSIVE_GATEWAY) {
      elementInstanceState.decrementNumberOfTakenSequenceFlows(
          value.getFlowScopeKey(), target.getId(), sequenceFlow.getId());
    }
  }
}
