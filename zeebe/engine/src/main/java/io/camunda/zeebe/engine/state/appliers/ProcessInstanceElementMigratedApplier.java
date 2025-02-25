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
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

/** Applies state changes for `ProcessInstance:Element_Migrated` */
final class ProcessInstanceElementMigratedApplier
    implements TypedEventApplier<ProcessInstanceIntent, ProcessInstanceRecord> {

  private final MutableElementInstanceState elementInstanceState;
  private final ProcessState processState;

  public ProcessInstanceElementMigratedApplier(
      final MutableElementInstanceState elementInstanceState, final ProcessState processState) {
    this.elementInstanceState = elementInstanceState;
    this.processState = processState;
  }

  @Override
  public void applyState(final long elementInstanceKey, final ProcessInstanceRecord value) {
    if (value.getBpmnElementType() == BpmnElementType.SEQUENCE_FLOW) {
      migrateTakenSequenceFlow(value);
      return;
    }

    final var previousProcessDefinitionKey = new AtomicLong();
    elementInstanceState.updateInstance(
        elementInstanceKey,
        elementInstance -> {
          previousProcessDefinitionKey.set(elementInstance.getValue().getProcessDefinitionKey());
          elementInstance
              .getValue()
              .setProcessDefinitionKey(value.getProcessDefinitionKey())
              .setBpmnProcessId(value.getBpmnProcessId())
              .setVersion(value.getVersion())
              .setElementId(value.getElementId())
              .setFlowScopeKey(value.getFlowScopeKey())
              .setElementInstancePath(value.getElementInstancePath())
              .setProcessDefinitionPath(value.getProcessDefinitionPath())
              .setCallingElementPath(value.getCallingElementPath());
        });

    if (value.getBpmnElementType() == BpmnElementType.PROCESS) {
      elementInstanceState.deleteProcessInstanceKeyByDefinitionKey(
          value.getProcessInstanceKey(), previousProcessDefinitionKey.get());
      elementInstanceState.insertProcessInstanceKeyByDefinitionKey(
          value.getProcessInstanceKey(), value.getProcessDefinitionKey());
    }
  }

  private void migrateTakenSequenceFlow(final ProcessInstanceRecord sequenceFlowRecord) {
    final ExecutableSequenceFlow sequenceFlow =
        processState.getFlowElement(
            sequenceFlowRecord.getProcessDefinitionKey(),
            sequenceFlowRecord.getTenantId(),
            sequenceFlowRecord.getElementIdBuffer(),
            ExecutableSequenceFlow.class);
    final var migratedSequenceFlowId = sequenceFlow.getId();
    final var targetGatewayId = sequenceFlow.getTarget().getId();

    elementInstanceState.visitTakenSequenceFlows(
        sequenceFlowRecord.getFlowScopeKey(),
        (flowScopeKey, sourceGatewayId, sequenceFlowId, number) -> {
          if (BufferUtil.equals(migratedSequenceFlowId, sequenceFlowId)) {
            IntStream.range(0, number)
                .forEach(
                    ignore -> {
                      // decrement the number of taken sequence flows for the old gateway
                      elementInstanceState.decrementNumberOfTakenSequenceFlows(
                          flowScopeKey, sourceGatewayId, sequenceFlowId);
                      // increment the number of taken sequence flows for the new gateway
                      elementInstanceState.incrementNumberOfTakenSequenceFlows(
                          flowScopeKey, targetGatewayId, sequenceFlowId);
                    });
          }
        });
  }
}
