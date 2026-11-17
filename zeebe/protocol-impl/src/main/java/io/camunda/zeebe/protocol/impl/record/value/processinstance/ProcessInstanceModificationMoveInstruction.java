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
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue.ProcessInstanceModificationMoveInstructionValue;
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue.ProcessInstanceModificationVariableInstructionValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.ArrayList;
import java.util.List;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;

@JsonIgnoreProperties({
  /* encodedLength and empty were inherited from ObjectValue, length is inherited from
  MsgpackBufferValue; they have no purpose in exported JSON records*/
  "encodedLength",
  "empty",
  "length"
})
@MsgpackPojo
@NullMarked
public final class ProcessInstanceModificationMoveInstruction
    extends MsgpackBufferValue<ProcessInstanceModificationMoveInstruction>
    implements ProcessInstanceModificationMoveInstructionValue {

  DirectBuffer sourceElementId = Decoding.EMPTY;
  long sourceElementInstanceKey = -1L;
  DirectBuffer targetElementId = Decoding.EMPTY;
  final List<ProcessInstanceModificationVariableInstruction> variableInstructions =
      new ArrayList<>();
  long ancestorScopeKey = -1L;
  boolean inferAncestorScopeFromSourceHierarchy = false;
  boolean useSourceParentKeyAsAncestorScopeKey = false;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public String getSourceElementId() {
    return BufferUtil.bufferAsString(sourceElementId);
  }

  @Override
  public long getSourceElementInstanceKey() {
    return sourceElementInstanceKey;
  }

  @Override
  public String getTargetElementId() {
    return BufferUtil.bufferAsString(targetElementId);
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
  public long getAncestorScopeKey() {
    return ancestorScopeKey;
  }

  public ProcessInstanceModificationMoveInstruction setAncestorScopeKey(
      final long ancestorScopeKey) {
    this.ancestorScopeKey = ancestorScopeKey;
    return this;
  }

  @Override
  public boolean isInferAncestorScopeFromSourceHierarchy() {
    return inferAncestorScopeFromSourceHierarchy;
  }

  public ProcessInstanceModificationMoveInstruction setInferAncestorScopeFromSourceHierarchy(
      final boolean inferAncestorScopeFromSource) {
    inferAncestorScopeFromSourceHierarchy = inferAncestorScopeFromSource;
    return this;
  }

  @Override
  public boolean isUseSourceParentKeyAsAncestorScopeKey() {
    return useSourceParentKeyAsAncestorScopeKey;
  }

  public ProcessInstanceModificationMoveInstruction setUseSourceParentKeyAsAncestorScopeKey(
      final boolean useSourceParentKey) {
    useSourceParentKeyAsAncestorScopeKey = useSourceParentKey;
    return this;
  }

  public ProcessInstanceModificationMoveInstruction setTargetElementId(
      final String targetElementId) {
    this.targetElementId = BufferUtil.wrapString(targetElementId);
    return this;
  }

  public ProcessInstanceModificationMoveInstruction setSourceElementInstanceKey(
      final long sourceElementInstanceKey) {
    this.sourceElementInstanceKey = sourceElementInstanceKey;
    return this;
  }

  public ProcessInstanceModificationMoveInstruction setSourceElementId(
      final String sourceElementId) {
    this.sourceElementId = BufferUtil.wrapString(sourceElementId);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getTargetElementIdBuffer() {
    return targetElementId;
  }

  @JsonIgnore
  public DirectBuffer getSourceElementIdBuffer() {
    return sourceElementId;
  }

  /** Returns true if this record has variable instructions, otherwise false. */
  @JsonIgnore
  public boolean hasVariableInstructions() {
    return !variableInstructions.isEmpty();
  }

  public ProcessInstanceModificationMoveInstruction addVariableInstruction(
      final ProcessInstanceModificationVariableInstruction variableInstruction) {
    final var added = new ProcessInstanceModificationVariableInstruction();
    added.copy(variableInstruction);
    variableInstructions.add(added);
    return this;
  }

  public ProcessInstanceModificationMoveInstruction copy(
      final ProcessInstanceModificationMoveInstructionValue object) {
    setSourceElementId(object.getSourceElementId());
    setSourceElementInstanceKey(object.getSourceElementInstanceKey());
    setTargetElementId(object.getTargetElementId());
    object.getVariableInstructions().stream()
        .map(ProcessInstanceModificationVariableInstruction.class::cast)
        .forEach(this::addVariableInstruction);
    setAncestorScopeKey(object.getAncestorScopeKey());
    setInferAncestorScopeFromSourceHierarchy(object.isInferAncestorScopeFromSourceHierarchy());
    setUseSourceParentKeyAsAncestorScopeKey(object.isUseSourceParentKeyAsAncestorScopeKey());
    return this;
  }

  @Override
  protected MsgpackCodec<ProcessInstanceModificationMoveInstruction> codec() {
    return ProcessInstanceModificationMoveInstructionCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<ProcessInstanceModificationMoveInstruction> ops() {
    return ProcessInstanceModificationMoveInstructionOps.INSTANCE;
  }
}
