/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.processinstance;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.RequiredLong;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.agrona.DirectBuffer;
import org.agrona.collections.LongArrayList;
import org.jspecify.annotations.NullMarked;

@MsgpackPojo
@NullMarked
public final class ProcessInstanceModificationRecord
    extends MsgpackBufferValue<ProcessInstanceModificationRecord>
    implements MsgpackRecordValue, ProcessInstanceModificationRecordValue {

  public static final StringValue TENANT_ID_KEY = new StringValue("tenantId");

  final RequiredLong processInstanceKey = new RequiredLong();
  final List<ProcessInstanceModificationTerminateInstruction> terminateInstructions =
      new ArrayList<>();
  final List<ProcessInstanceModificationActivateInstruction> activateInstructions =
      new ArrayList<>();
  final List<ProcessInstanceModificationMoveInstruction> moveInstructions = new ArrayList<>();
  final LongArrayList activatedElementInstanceKeys = new LongArrayList();
  DirectBuffer tenantId = BufferUtil.wrapString(TenantOwned.DEFAULT_TENANT_IDENTIFIER);
  long rootProcessInstanceKey = -1L;
  int storageOrdinal = 0;
  long processDefinitionKey = -1L;
  DirectBuffer bpmnProcessId = Decoding.EMPTY;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  /**
   * This method is expensive because it copies each element before returning it. It is recommended
   * to use {@link #hasTerminateInstructions()} before calling this.
   *
   * <p>{@inheritDoc}
   */
  @Override
  public List<ProcessInstanceModificationTerminateInstructionValue> getTerminateInstructions() {
    return terminateInstructions.stream()
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
    return activateInstructions.stream()
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
    return moveInstructions.stream()
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
    final Set<Long> ancestorScopeKeys = new HashSet<>();
    for (int i = 0; i < activatedElementInstanceKeys.size(); i++) {
      ancestorScopeKeys.add(activatedElementInstanceKeys.getLong(i));
    }
    // For backwards compatibility's sake we have to add the ancestor scope keys of all activate
    // instructions, as from version 8.1.3 on the activatedElementInstanceKeys property is no longer
    // filled.
    for (final var instruction : getActivateInstructions()) {
      ancestorScopeKeys.addAll(instruction.getAncestorScopeKeys());
    }
    return ancestorScopeKeys;
  }

  @Override
  public long getRootProcessInstanceKey() {
    return rootProcessInstanceKey;
  }

  public ProcessInstanceModificationRecord setRootProcessInstanceKey(final long key) {
    rootProcessInstanceKey = key;
    return this;
  }

  /** Returns true if this record has terminate instructions, otherwise false. */
  @JsonIgnore
  public boolean hasTerminateInstructions() {
    return !terminateInstructions.isEmpty();
  }

  public ProcessInstanceModificationRecord addTerminateInstruction(
      final ProcessInstanceModificationTerminateInstructionValue terminateInstruction) {
    final var instruction = new ProcessInstanceModificationTerminateInstruction();
    instruction.copy(terminateInstruction);
    terminateInstructions.add(instruction);
    return this;
  }

  /** Returns true if this record has move instructions, otherwise false. */
  @JsonIgnore
  public boolean hasMoveInstructions() {
    return !moveInstructions.isEmpty();
  }

  public ProcessInstanceModificationRecord addMoveInstruction(
      final ProcessInstanceModificationMoveInstructionValue moveInstruction) {
    final var instruction = new ProcessInstanceModificationMoveInstruction();
    instruction.copy(moveInstruction);
    moveInstructions.add(instruction);
    return this;
  }

  /** Returns true if this record has activate instructions, otherwise false. */
  @JsonIgnore
  public boolean hasActivateInstructions() {
    return !activateInstructions.isEmpty();
  }

  public ProcessInstanceModificationRecord addActivateInstruction(
      final ProcessInstanceModificationActivateInstruction activateInstruction) {
    final var instruction = new ProcessInstanceModificationActivateInstruction();
    instruction.copy(activateInstruction);
    activateInstructions.add(instruction);
    return this;
  }

  @Override
  public long getProcessInstanceKey() {
    return processInstanceKey.get();
  }

  public ProcessInstanceModificationRecord setProcessInstanceKey(final long processInstanceKey) {
    this.processInstanceKey.set(processInstanceKey);
    return this;
  }

  @Override
  public long getProcessDefinitionKey() {
    return processDefinitionKey;
  }

  public ProcessInstanceModificationRecord setProcessDefinitionKey(
      final long processDefinitionKey) {
    this.processDefinitionKey = processDefinitionKey;
    return this;
  }

  @Override
  public String getBpmnProcessId() {
    return BufferUtil.bufferAsString(bpmnProcessId);
  }

  public ProcessInstanceModificationRecord setBpmnProcessId(final String bpmnProcessId) {
    this.bpmnProcessId = BufferUtil.wrapString(bpmnProcessId);
    return this;
  }

  @Override
  public String getTenantId() {
    return BufferUtil.bufferAsString(tenantId);
  }

  public ProcessInstanceModificationRecord setTenantId(final String tenantId) {
    this.tenantId = BufferUtil.wrapString(tenantId);
    return this;
  }

  @Override
  public int getStorageOrdinal() {
    return storageOrdinal;
  }

  public ProcessInstanceModificationRecord setStorageOrdinal(final int storageOrdinal) {
    this.storageOrdinal = storageOrdinal;
    return this;
  }

  @Override
  protected MsgpackCodec<ProcessInstanceModificationRecord> codec() {
    return ProcessInstanceModificationRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<ProcessInstanceModificationRecord> ops() {
    return ProcessInstanceModificationRecordOps.INSTANCE;
  }
}
