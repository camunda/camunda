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

/** Applies state changes for `ProcessInstance:Sequence_Flow_Taken` */
final class ProcessInstanceSequenceFlowTakenApplier
    implements TypedEventApplier<ProcessInstanceIntent, ProcessInstanceRecord> {

  private final MutableElementInstanceState elementInstanceState;
  private final MutableProcessState processState;

  public ProcessInstanceSequenceFlowTakenApplier(
      final MutableElementInstanceState elementInstanceState,
      final MutableProcessState processState) {
    this.elementInstanceState = elementInstanceState;
    this.processState = processState;
  }

  @Override
  public void applyState(final long key, final ProcessInstanceRecord value) {
    // We need to keep track of the active sequence flows to not complete the process instances to
    // early. On completing of the process instance we verify that we have no more active element
    // instances and active sequence flows. Since sequence flows are no elements, we don't want to
    // have element instances for them.
    //
    // This is necessary for concurrent flows, where for example the end event is reached on one
    // path and on the other path the sequence flow is currently active.
    //
    // See discussion in https://github.com/camunda/camunda/pull/6562
    final var sequenceFlow =
        processState.getFlowElement(
            value.getProcessDefinitionKey(),
            value.getTenantId(),
            value.getElementIdBuffer(),
            ExecutableSequenceFlow.class);
    final var target = sequenceFlow.getTarget();

    final var flowScopeInstance = elementInstanceState.getInstance(value.getFlowScopeKey());
    flowScopeInstance.incrementActiveSequenceFlows();
    // Stores the pending sequence flow that helps to determine
    // if an inclusive gateway can be activated or not.
    flowScopeInstance.addActiveSequenceFlowId(sequenceFlow.getId());
    elementInstanceState.updateInstance(flowScopeInstance);

    if (target.getElementType() == BpmnElementType.PARALLEL_GATEWAY) {
      // stores which sequence flows of the parallel gateway are taken
      // - the gateway is only activated if all incoming sequence flows are taken at least once
      elementInstanceState.incrementNumberOfTakenSequenceFlows(
          value.getFlowScopeKey(), target.getId(), sequenceFlow.getId());
    }

    if (target.getElementType() == BpmnElementType.INCLUSIVE_GATEWAY) {
      // stores which sequence flows of the inclusive gateway are taken
      // - the gateway is only activated if the fulfilled incoming sequence flows are taken at
      // least once
      elementInstanceState.incrementNumberOfTakenSequenceFlows(
          value.getFlowScopeKey(), target.getId(), sequenceFlow.getId());
    }
  }
}
