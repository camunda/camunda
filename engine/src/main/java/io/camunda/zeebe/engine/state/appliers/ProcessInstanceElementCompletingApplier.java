/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;

/** Applies state changes for `ProcessInstance:Element_Completing` */
final class ProcessInstanceElementCompletingApplier
    implements TypedEventApplier<ProcessInstanceIntent, ProcessInstanceRecord> {

  private final MutableElementInstanceState elementInstanceState;

  public ProcessInstanceElementCompletingApplier(
      final MutableElementInstanceState elementInstanceState) {
    this.elementInstanceState = elementInstanceState;
  }

  @Override
  public void applyState(final long elementInstanceKey, final ProcessInstanceRecord value) {
    elementInstanceState.updateInstance(
        elementInstanceKey,
        instance -> instance.setState(ProcessInstanceIntent.ELEMENT_COMPLETING));

    final var flowScopeInstance = elementInstanceState.getInstance(value.getFlowScopeKey());

    if (flowScopeInstance == null) {
      return;
    }

    final var flowScopeElementType = flowScopeInstance.getValue().getBpmnElementType();
    manageMultiInstance(flowScopeInstance, flowScopeElementType);
  }

  private void manageMultiInstance(
      final ElementInstance flowScopeInstance, final BpmnElementType flowScopeElementType) {
    if (flowScopeElementType == BpmnElementType.MULTI_INSTANCE_BODY) {
      // update the numberOfCompletedInstances of the multi-instance body
      flowScopeInstance.incrementNumberOfCompletedElementInstances();
      elementInstanceState.updateInstance(flowScopeInstance);
    }
  }
}
