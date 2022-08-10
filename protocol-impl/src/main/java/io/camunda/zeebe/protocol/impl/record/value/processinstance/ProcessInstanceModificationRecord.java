/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.impl.record.value.processinstance;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.RecordValueWithTenant;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;

public final class ProcessInstanceModificationRecord extends UnifiedRecordValue
    implements ProcessInstanceModificationRecordValue {

  private final LongProperty processInstanceKeyProperty = new LongProperty("processInstanceKey");
  private final ArrayProperty<ProcessInstanceModificationTerminateInstruction>
      terminateInstructionsProperty =
          new ArrayProperty<>(
              "terminateInstructions", new ProcessInstanceModificationTerminateInstruction());
  private final ArrayProperty<ProcessInstanceModificationActivateInstruction>
      activateInstructionsProperty =
          new ArrayProperty<>(
              "activateInstructions", new ProcessInstanceModificationActivateInstruction());
  private final StringProperty tenantIdProp =
      new StringProperty("tenantId", RecordValueWithTenant.DEFAULT_TENANT_ID);

  public ProcessInstanceModificationRecord() {
    declareProperty(processInstanceKeyProperty)
        .declareProperty(terminateInstructionsProperty)
        .declareProperty(activateInstructionsProperty)
        .declareProperty(tenantIdProp);
  }

  /**
   * This method is expensive because it copies each element before returning it. It is recommended
   * to use {@link #hasTerminateInstructions()} before calling this.
   *
   * <p>{@inheritDoc}
   */
  @Override
  public List<ProcessInstanceModificationTerminateInstructionValue> getTerminateInstructions() {
    // we need to make a copy of each element in the ArrayProperty while iterating it because the
    // inner values are updated during the iteration
    return terminateInstructionsProperty.stream()
        .map(
            element -> {
              final var elementCopy = new ProcessInstanceModificationTerminateInstruction();
              elementCopy.copy(element);
              return (ProcessInstanceModificationTerminateInstructionValue) elementCopy;
            })
        .toList();
  }

  /**
   * This method is expensive because it copies each element before returning it. It is recommended
   * to use {@link #hasActivateInstructions()} before calling this.
   *
   * <p>{@inheritDoc}
   */
  @Override
  public List<ProcessInstanceModificationActivateInstructionValue> getActivateInstructions() {
    // we need to make a copy of each element in the ArrayProperty while iterating it because the
    // inner values are updated during the iteration
    return activateInstructionsProperty.stream()
        .map(
            element -> {
              final var elementCopy = new ProcessInstanceModificationActivateInstruction();
              elementCopy.copy(element);
              return (ProcessInstanceModificationActivateInstructionValue) elementCopy;
            })
        .toList();
  }

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantIdProp.getValue());
  }

  public ProcessInstanceModificationRecord setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }

  /** Returns true if this record has terminate instructions, otherwise false. */
  @JsonIgnore
  public boolean hasTerminateInstructions() {
    return !terminateInstructionsProperty.isEmpty();
  }

  public ProcessInstanceModificationRecord addTerminateInstruction(
      final ProcessInstanceModificationTerminateInstructionValue terminateInstruction) {
    terminateInstructionsProperty.add().copy(terminateInstruction);
    return this;
  }

  /** Returns true if this record has activate instructions, otherwise false. */
  @JsonIgnore
  public boolean hasActivateInstructions() {
    return !activateInstructionsProperty.isEmpty();
  }

  public ProcessInstanceModificationRecord addActivateInstruction(
      final ProcessInstanceModificationActivateInstruction activateInstruction) {
    activateInstructionsProperty.add().copy(activateInstruction);
    return this;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKeyProperty.getValue();
  }

  public ProcessInstanceModificationRecord setProcessInstanceKey(final long processInstanceKey) {
    processInstanceKeyProperty.setValue(processInstanceKey);
    return this;
  }

  /** hashCode relies on implementation provided by {@link ObjectValue#hashCode()} */
  @Override
  public int hashCode() {
    return super.hashCode();
  }

  /** equals relies on implementation provided by {@link ObjectValue#equals(Object)} */
  @Override
  public boolean equals(final Object o) {
    return super.equals(o);
  }
}
