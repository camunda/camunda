/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.processinstance;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue.ProcessInstanceModificationActivateInstructionValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue.ProcessInstanceModificationVariableInstructionValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;
import org.agrona.collections.LongArrayList;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@JsonIgnoreProperties({
  /* encodedLength and empty were inherited from ObjectValue, length is inherited from
  MsgpackBufferValue; they have no purpose in exported JSON records*/
  "encodedLength",
  "empty",
  "length"
})
@MsgpackPojo
@NullMarked
public final class ProcessInstanceModificationActivateInstruction
    extends MsgpackBufferValue<ProcessInstanceModificationActivateInstruction>
    implements ProcessInstanceModificationActivateInstructionValue {

  @Nullable DirectBuffer elementId;
  long ancestorScopeKey = -1L;
  final List<ProcessInstanceModificationVariableInstruction> variableInstructions =
      new ArrayList<>();
  final LongArrayList ancestorScopeKeys = new LongArrayList();
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public String getElementId() {
    return BufferUtil.bufferAsString(getElementIdBuffer());
  }

  @Override
  public long getAncestorScopeKey() {
    return ancestorScopeKey;
  }

  /**
   * This method is expensive because it copies each element before returning it. It is recommended
   * to use {@link #hasVariableInstructions()} before calling this.
   *
   * <p>{@inheritDoc}
   */
  @Override
  public List<ProcessInstanceModificationVariableInstructionValue> getVariableInstructions() {
    return variableInstructions.stream()
        .map(
            instruction -> {
              final var copy = new ProcessInstanceModificationVariableInstruction();
              copy.copy(instruction);
              return (ProcessInstanceModificationVariableInstructionValue) copy;
            })
        .toList();
  }

  @Override
  public Set<Long> getAncestorScopeKeys() {
    return ancestorScopeKeys.stream().collect(Collectors.toSet());
  }

  public ProcessInstanceModificationActivateInstruction setAncestorScopeKey(
      final long ancestorScopeKey) {
    this.ancestorScopeKey = ancestorScopeKey;
    return this;
  }

  public ProcessInstanceModificationActivateInstruction setElementId(final String elementId) {
    this.elementId = BufferUtil.wrapString(elementId);
    return this;
  }

  /** Returns true if this record has variable instructions, otherwise false. */
  @JsonIgnore
  public boolean hasVariableInstructions() {
    return !variableInstructions.isEmpty();
  }

  public ProcessInstanceModificationActivateInstruction addVariableInstruction(
      final ProcessInstanceModificationVariableInstruction variableInstruction) {
    final var added = new ProcessInstanceModificationVariableInstruction();
    added.copy(variableInstruction);
    variableInstructions.add(added);
    return this;
  }

  public ProcessInstanceModificationActivateInstruction addAncestorScopeKeys(
      final Set<Long> flowScopeKeys) {
    flowScopeKeys.forEach(ancestorScopeKeys::addLong);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getElementIdBuffer() {
    return Decoding.required(elementId, "elementId");
  }

  public void copy(final ProcessInstanceModificationActivateInstruction object) {
    setElementId(object.getElementId());
    setAncestorScopeKey(object.getAncestorScopeKey());
    object.getVariableInstructions().stream()
        .map(ProcessInstanceModificationVariableInstruction.class::cast)
        .forEach(this::addVariableInstruction);
    addAncestorScopeKeys(object.getAncestorScopeKeys());
  }

  @Override
  protected MsgpackCodec<ProcessInstanceModificationActivateInstruction> codec() {
    return ProcessInstanceModificationActivateInstructionCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<ProcessInstanceModificationActivateInstruction> ops() {
    return ProcessInstanceModificationActivateInstructionOps.INSTANCE;
  }
}
