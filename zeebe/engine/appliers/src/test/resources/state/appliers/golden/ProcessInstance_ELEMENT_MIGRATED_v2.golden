/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.processing.deployment.model.element.ExecutableFlowElementContainer;
import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.immutable.ProcessState;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.engine.state.mutable.MutableMessageState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.concurrent.atomic.AtomicLong;
import org.agrona.DirectBuffer;

/** Applies state changes for `ProcessInstance:Element_Migrated` */
final class ProcessInstanceElementMigratedV2Applier
    implements TypedEventApplier<ProcessInstanceIntent, ProcessInstanceRecord> {

  private final MutableElementInstanceState elementInstanceState;
  private final ProcessState processState;
  private final MutableMessageState messageState;

  public ProcessInstanceElementMigratedV2Applier(
      final MutableElementInstanceState elementInstanceState,
      final ProcessState processState,
      final MutableMessageState messageState) {
    this.elementInstanceState = elementInstanceState;
    this.processState = processState;
    this.messageState = messageState;
  }

  @Override
  public void applyState(final long elementInstanceKey, final ProcessInstanceRecord value) {
    if (value.getBpmnElementType() == BpmnElementType.PROCESS) {
      migrateCorrelatedMessageStartEvent(elementInstanceKey, value);
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

  private void migrateCorrelatedMessageStartEvent(
      final long elementInstanceKey, final ProcessInstanceRecord value) {

    final var instance = elementInstanceState.getInstance(elementInstanceKey).getValue();
    final DirectBuffer previousBpmnProcessId = instance.getBpmnProcessIdBuffer();
    final DirectBuffer currentBpmnProcessId = value.getBpmnProcessIdBuffer();
    final DirectBuffer correlationKey =
        messageState.getProcessInstanceCorrelationKey(value.getProcessInstanceKey());

    if (isCorrelationKeyAbsent(correlationKey)) {
      // no need to update correlation key relations since there isn't one
      return;
    }

    final boolean isTargetWithMessageStartEvent =
        processState
            .getFlowElement(
                value.getProcessDefinitionKey(),
                value.getTenantId(),
                value.getElementIdBuffer(),
                ExecutableFlowElementContainer.class)
            .hasMessageStartEvent();
    final boolean hasBpmnProcessIdChanged =
        !BufferUtil.equals(previousBpmnProcessId, currentBpmnProcessId);

    if (isTargetWithMessageStartEvent && hasBpmnProcessIdChanged) {
      // unlock the previous process instance's process id
      messageState.removeActiveProcessInstance(previousBpmnProcessId, correlationKey);
      // lock the new process instance's process id
      messageState.putActiveProcessInstance(currentBpmnProcessId, correlationKey);
    }

    // clean up the correlation key relation if the target process doesn't have a message start
    // event at all
    if (!isTargetWithMessageStartEvent) {
      messageState.removeActiveProcessInstance(previousBpmnProcessId, correlationKey);
      messageState.removeProcessInstanceCorrelationKey(value.getProcessInstanceKey());
    }
  }

  private boolean isCorrelationKeyAbsent(final DirectBuffer correlationKey) {
    return correlationKey == null || correlationKey.capacity() == 0;
  }
}
