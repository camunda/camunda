/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableCallActivity;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableEndEvent;
import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.instance.ElementInstance;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableEventScopeInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableMultiInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableVariableState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;

/** Applies state changes for `ProcessInstance:Element_Completed` */
final class ProcessInstanceElementCompletedApplier
    implements TypedEventApplier<ProcessInstanceIntent, ProcessInstanceRecord> {

  private final MutableElementInstanceState elementInstanceState;
  private final MutableEventScopeInstanceState eventScopeInstanceState;
  private final MutableVariableState variableState;
  private final ProcessState processState;
  private final MutableMultiInstanceState multiInstanceState;
  private final BufferedStartMessageEventStateApplier bufferedStartMessageEventStateApplier;

  public ProcessInstanceElementCompletedApplier(
      final MutableElementInstanceState elementInstanceState,
      final MutableEventScopeInstanceState eventScopeInstanceState,
      final MutableVariableState variableState,
      final ProcessState processState,
      final MutableMultiInstanceState multiInstanceState,
      final BufferedStartMessageEventStateApplier bufferedStartMessageEventStateApplier) {
    this.elementInstanceState = elementInstanceState;
    this.eventScopeInstanceState = eventScopeInstanceState;
    this.variableState = variableState;
    this.processState = processState;
    this.multiInstanceState = multiInstanceState;
    this.bufferedStartMessageEventStateApplier = bufferedStartMessageEventStateApplier;
  }

  @Override
  public void applyState(final long key, final ProcessInstanceRecord value) {

    final var parentElementInstanceKey = value.getParentElementInstanceKey();

    if (isChildProcess(value, parentElementInstanceKey)) {
      propagateVariables(key, parentElementInstanceKey);
    }

    bufferedStartMessageEventStateApplier.removeMessageLock(value);

    if (value.getBpmnElementType() == BpmnElementType.MULTI_INSTANCE_BODY) {
      multiInstanceState.deleteInputCollection(key);
    }

    eventScopeInstanceState.deleteInstance(key);
    elementInstanceState.removeInstance(key);

    final var flowScopeInstance = elementInstanceState.getInstance(value.getFlowScopeKey());

    if (flowScopeInstance == null) {
      return;
    }

    final var flowScopeElementType = flowScopeInstance.getValue().getBpmnElementType();
    manageMultiInstance(flowScopeInstance, flowScopeElementType);

    if (isTerminateEndEvent(value)) {
      flowScopeInstance.resetActiveSequenceFlows();
      flowScopeInstance.setInterruptingElementId(value.getElementIdBuffer());
      elementInstanceState.updateInstance(flowScopeInstance);
    }
  }

  private boolean isChildProcess(
      final ProcessInstanceRecord value, final long parentElementInstanceKey) {
    return parentElementInstanceKey > 0 && value.getBpmnElementType() == BpmnElementType.PROCESS;
  }

  private void propagateVariables(final long key, final long parentElementInstanceKey) {
    final var parentElementInstance = elementInstanceState.getInstance(parentElementInstanceKey);

    final var elementId = parentElementInstance.getValue().getElementIdBuffer();

    final var callActivity =
        processState.getFlowElement(
            parentElementInstance.getValue().getProcessDefinitionKey(),
            parentElementInstance.getValue().getTenantId(),
            elementId,
            ExecutableCallActivity.class);

    if (callActivity.getOutputMappings().isPresent()
        || callActivity.isPropagateAllChildVariablesEnabled()) {
      final var variables = variableState.getVariablesAsDocument(key);
      eventScopeInstanceState.triggerEvent(
          parentElementInstanceKey,
          parentElementInstanceKey,
          elementId,
          variables,
          parentElementInstance.getValue().getProcessInstanceKey());
    }
  }

  private void manageMultiInstance(
      final ElementInstance flowScopeInstance, final BpmnElementType flowScopeElementType) {
    if (flowScopeElementType == BpmnElementType.MULTI_INSTANCE_BODY) {
      // update the numberOfCompletedInstances of the multi-instance body
      flowScopeInstance.incrementNumberOfCompletedElementInstances();
      elementInstanceState.updateInstance(flowScopeInstance);
    }
  }

  private boolean isTerminateEndEvent(final ProcessInstanceRecord value) {
    if (value.getBpmnElementType().equals(BpmnElementType.END_EVENT)) {
      final var element =
          processState.getFlowElement(
              value.getProcessDefinitionKey(),
              value.getTenantId(),
              value.getElementIdBuffer(),
              ExecutableEndEvent.class);
      return element.isTerminateEndEvent();
    }
    return false;
  }
}
