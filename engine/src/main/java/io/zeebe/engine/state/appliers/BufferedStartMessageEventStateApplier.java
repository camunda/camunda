/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state.appliers;

import io.zeebe.engine.processing.deployment.model.element.ExecutableFlowElementContainer;
import io.zeebe.engine.state.immutable.ProcessState;
import io.zeebe.engine.state.mutable.MutableMessageState;
import io.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.zeebe.protocol.record.value.BpmnElementType;

public final class BufferedStartMessageEventStateApplier {

  private final ProcessState processState;
  private final MutableMessageState messageState;

  public BufferedStartMessageEventStateApplier(
      final ProcessState processState, final MutableMessageState messageState) {
    this.processState = processState;
    this.messageState = messageState;
  }

  /**
   * If a process instance is created by a message then it creates a lock for the instance to avoid
   * that another instance can be created for the same message name and correlation key. This method
   * removes the lock. It should be called when the process instance transitions to completed or
   * terminated.
   *
   * @param record the record of the process instance that has ended
   */
  public void removeMessageLock(final ProcessInstanceRecord record) {

    if (record.getBpmnElementType() == BpmnElementType.PROCESS) {
      final var processElement = getProcessElement(record);

      if (processElement.hasMessageStartEvent()) {
        removeProcessInstanceMessageLock(record);
      }
    }
  }

  private ExecutableFlowElementContainer getProcessElement(final ProcessInstanceRecord record) {
    return processState.getFlowElement(
        record.getProcessDefinitionKey(),
        record.getElementIdBuffer(),
        ExecutableFlowElementContainer.class);
  }

  private void removeProcessInstanceMessageLock(final ProcessInstanceRecord record) {

    final var processInstanceKey = record.getProcessInstanceKey();
    final var correlationKey = messageState.getProcessInstanceCorrelationKey(processInstanceKey);

    if (correlationKey != null) {
      messageState.removeProcessInstanceCorrelationKey(processInstanceKey);
      messageState.removeActiveProcessInstance(record.getBpmnProcessIdBuffer(), correlationKey);
    }
  }
}
