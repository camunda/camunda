/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableElementInstanceState;
import io.camunda.zeebe.protocol.impl.record.value.processinstance.ProcessInstanceRecord;
import io.camunda.zeebe.protocol.record.intent.ProcessInstanceIntent;
import io.camunda.zeebe.protocol.record.value.BpmnElementType;
import java.util.concurrent.atomic.AtomicLong;

/** Applies state changes for `ProcessInstance:Element_Migrated` */
final class ProcessInstanceElementMigratedApplier
    implements TypedEventApplier<ProcessInstanceIntent, ProcessInstanceRecord> {

  private final MutableElementInstanceState elementInstanceState;

  public ProcessInstanceElementMigratedApplier(
      final MutableElementInstanceState elementInstanceState) {
    this.elementInstanceState = elementInstanceState;
  }

  @Override
  public void applyState(final long elementInstanceKey, final ProcessInstanceRecord value) {
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
}
