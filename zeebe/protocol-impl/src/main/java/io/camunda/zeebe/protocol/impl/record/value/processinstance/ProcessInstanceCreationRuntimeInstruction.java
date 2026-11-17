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
import io.camunda.zeebe.protocol.record.value.ProcessInstanceCreationRecordValue.ProcessInstanceCreationRuntimeInstructionValue;
import io.camunda.zeebe.protocol.record.value.RuntimeInstructionType;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
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
public final class ProcessInstanceCreationRuntimeInstruction
    extends MsgpackBufferValue<ProcessInstanceCreationRuntimeInstruction>
    implements ProcessInstanceCreationRuntimeInstructionValue {

  @Nullable RuntimeInstructionType type;
  DirectBuffer afterElementId = Decoding.EMPTY;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  /**
   * Gets the type of this instruction as an enum.
   *
   * @return the type as an enum
   */
  @Override
  public RuntimeInstructionType getType() {
    return Decoding.required(type, "type");
  }

  public ProcessInstanceCreationRuntimeInstruction setType(final RuntimeInstructionType type) {
    this.type = type;
    return this;
  }

  @Override
  public String getAfterElementId() {
    return BufferUtil.bufferAsString(afterElementId);
  }

  public ProcessInstanceCreationRuntimeInstruction setAfterElementId(final String afterElementId) {
    this.afterElementId = BufferUtil.wrapString(afterElementId);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getAfterElementIdBuffer() {
    return afterElementId;
  }

  public static ProcessInstanceCreationRuntimeInstruction createInstruction() {
    return new ProcessInstanceCreationRuntimeInstruction();
  }

  public void copy(final ProcessInstanceCreationRuntimeInstructionValue instruction) {
    setType(instruction.getType());
    setAfterElementId(instruction.getAfterElementId());
  }

  @Override
  protected MsgpackCodec<ProcessInstanceCreationRuntimeInstruction> codec() {
    return ProcessInstanceCreationRuntimeInstructionCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<ProcessInstanceCreationRuntimeInstruction> ops() {
    return ProcessInstanceCreationRuntimeInstructionOps.INSTANCE;
  }
}
