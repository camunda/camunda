/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.processinstance;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.msgpack.value.ObjectValue;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class ProcessInstanceModificationRecord extends UnifiedRecordValue
    implements ProcessInstanceModificationRecordValue {

  public static final StringValue TENANT_ID_KEY = new StringValue("tenantId");
  // Static StringValue keys to avoid memory waste
  private static final StringValue PROCESS_INSTANCE_KEY_KEY = new StringValue("processInstanceKey");
  private static final StringValue TERMINATE_INSTRUCTIONS_KEY =
      new StringValue("terminateInstructions");
  private static final StringValue ACTIVATE_INSTRUCTIONS_KEY =
      new StringValue("activateInstructions");
  private static final StringValue MOVE_INSTRUCTIONS_KEY = new StringValue("moveInstructions");
  private static final StringValue ACTIVATED_ELEMENT_INSTANCE_KEYS_KEY =
      new StringValue("activatedElementInstanceKeys");
  private static final StringValue ROOT_PROCESS_INSTANCE_KEY_KEY =
      new StringValue("rootProcessInstanceKey");
  private static final StringValue PROCESS_DEFINITION_KEY_KEY =
      new StringValue("processDefinitionKey");
  private static final StringValue BPMN_PROCESS_ID_KEY = new StringValue("bpmnProcessId");

  private final LongProperty processInstanceKeyProperty =
      new LongProperty(PROCESS_INSTANCE_KEY_KEY);
  private final LongProperty processDefinitionKeyProperty =
      new LongProperty(PROCESS_DEFINITION_KEY_KEY, -1L);
  private final StringProperty tenantIdProp =
      new StringProperty(TENANT_ID_KEY, TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  private final StringProperty bpmnProcessIdProperty = new StringProperty(BPMN_PROCESS_ID_KEY, "");

  private final ArrayProperty<ProcessInstanceModificationTerminateInstruction>
      terminateInstructionsProperty =
          new ArrayProperty<>(
              TERMINATE_INSTRUCTIONS_KEY, ProcessInstanceModificationTerminateInstruction::new);
  private final ArrayProperty<ProcessInstanceModificationActivateInstruction>
      activateInstructionsProperty =
          new ArrayProperty<>(
              ACTIVATE_INSTRUCTIONS_KEY, ProcessInstanceModificationActivateInstruction::new);
  private final ArrayProperty<ProcessInstanceModificationMoveInstruction> moveInstructionsProperty =
      new ArrayProperty<>(MOVE_INSTRUCTIONS_KEY, ProcessInstanceModificationMoveInstruction::new);

  private final ArrayProperty<LongValue> activatedElementInstanceKeys =
      new ArrayProperty<>(ACTIVATED_ELEMENT_INSTANCE_KEYS_KEY, LongValue::new);
  private final LongProperty rootProcessInstanceKeyProperty =
      new LongProperty(ROOT_PROCESS_INSTANCE_KEY_KEY, -1);

  public ProcessInstanceModificationRecord() {
    super(9);
    declareProperty(processInstanceKeyProperty)
        .declareProperty(terminateInstructionsProperty)
        .declareProperty(activateInstructionsProperty)
        .declareProperty(moveInstructionsProperty)
        .declareProperty(activatedElementInstanceKeys)
        .declareProperty(tenantIdProp)
        .declareProperty(rootProcessInstanceKeyProperty)
        .declareProperty(processDefinitionKeyProperty)
        .declareProperty(bpmnProcessIdProperty);
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

  /**
   * This method is expensive because it copies each element before returning it. It is recommended
   * to use {@link #hasMoveInstructions()} before calling this.
   *
   * <p>{@inheritDoc}
   */
  @Override
  public List<ProcessInstanceModificationMoveInstructionValue> getMoveInstructions() {
    // we need to make a copy of each element in the ArrayProperty while iterating it because the
    // inner values are updated during the iteration
    return moveInstructionsProperty.stream()
        .map(
            element -> {
              final var elementCopy = new ProcessInstanceModificationMoveInstruction();
              elementCopy.copy(element);
              return (ProcessInstanceModificationMoveInstructionValue) elementCopy;
            })
        .toList();
  }

  @Override
  public Set<Long> getAncestorScopeKeys() {
    final Set<Long> activatedElementInstanceKeys =
        this.activatedElementInstanceKeys.stream()
            .map(LongValue::getValue)
            .collect(Collectors.toSet());
    // For backwards compatibility's sake we have to add the ancestor scope keys of all activate
    // instructions, as from version 8.1.3 on the activatedElementInstanceKeys property is no longer
    // filled.
    activatedElementInstanceKeys.addAll(
        getActivateInstructions().stream()
            .map(ProcessInstanceModificationActivateInstructionValue::getAncestorScopeKeys)
            .flatMap(Set::stream)
            .collect(Collectors.toSet()));
    return activatedElementInstanceKeys;
  }

  @Override
  public long getRootProcessInstanceKey() {
    return rootProcessInstanceKeyProperty.getValue();
  }

  public ProcessInstanceModificationRecord setRootProcessInstanceKey(final long key) {
    rootProcessInstanceKeyProperty.setValue(key);
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

  /** Returns true if this record has move instructions, otherwise false. */
  @JsonIgnore
  public boolean hasMoveInstructions() {
    return !moveInstructionsProperty.isEmpty();
  }

  public ProcessInstanceModificationRecord addMoveInstruction(
      final ProcessInstanceModificationMoveInstructionValue moveInstruction) {
    moveInstructionsProperty.add().copy(moveInstruction);
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

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKeyProperty.getValue();
  }

  public ProcessInstanceModificationRecord setProcessDefinitionKey(
      final long processDefinitionKey) {
    processDefinitionKeyProperty.setValue(processDefinitionKey);
    return this;
  }

  @Override
  public String getBpmnProcessId() {
    return BufferUtil.bufferAsString(bpmnProcessIdProperty.getValue());
  }

  public ProcessInstanceModificationRecord setBpmnProcessId(final String bpmnProcessId) {
    bpmnProcessIdProperty.setValue(bpmnProcessId);
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

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantIdProp.getValue());
  }

  public ProcessInstanceModificationRecord setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }
}
