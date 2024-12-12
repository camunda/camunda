/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.processing.deployment.model.element.AbstractFlowElement;
import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowNode;
import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.agrona.DirectBuffer;

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
    // noElement is true when incoming sequence flows of the migrated gateway only partly taken
    final boolean noElement = elementInstanceState.getInstance(elementInstanceKey) == null;
    if (noElement && value.getBpmnElementType() == BpmnElementType.PARALLEL_GATEWAY) {
      migrateTakenSequenceFlowsForGateway(value);
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
              .setFlowScopeKey(value.getFlowScopeKey());
        });

    if (value.getBpmnElementType() == BpmnElementType.PROCESS) {
      elementInstanceState.deleteProcessInstanceKeyByDefinitionKey(
          value.getProcessInstanceKey(), previousProcessDefinitionKey.get());
      elementInstanceState.insertProcessInstanceKeyByDefinitionKey(
          value.getProcessInstanceKey(), value.getProcessDefinitionKey());
    }
  }

  private void migrateTakenSequenceFlowsForGateway(final ProcessInstanceRecord gatewayRecord) {
    final Set<DirectBuffer> incomingSequenceFlowIds =
        processState
            .getFlowElement(
                gatewayRecord.getProcessDefinitionKey(),
                gatewayRecord.getTenantId(),
                gatewayRecord.getElementIdBuffer(),
                ExecutableFlowNode.class)
            .getIncoming()
            .stream()
            .map(AbstractFlowElement::getId)
            .collect(Collectors.toSet());

    elementInstanceState.visitTakenSequenceFlows(
        gatewayRecord.getFlowScopeKey(),
        (flowScopeKey, gatewayElementId, sequenceFlowId, number) -> {
          if (incomingSequenceFlowIds.contains(sequenceFlowId)) {
            IntStream.range(0, number)
                .forEach(
                    ignore -> {
                      // decrement the number of taken sequence flows for the old gateway
                      elementInstanceState.decrementNumberOfTakenSequenceFlows(
                          flowScopeKey, gatewayElementId, sequenceFlowId);
                      // increment the number of taken sequence flows for the new gateway
                      elementInstanceState.incrementNumberOfTakenSequenceFlows(
                          flowScopeKey, gatewayRecord.getElementIdBuffer(), sequenceFlowId);
                    });
          }
        });
  }
}
