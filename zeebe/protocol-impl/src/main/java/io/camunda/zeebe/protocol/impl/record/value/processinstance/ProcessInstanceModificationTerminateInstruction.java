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
import io.camunda.zeebe.protocol.record.value.ProcessInstanceModificationRecordValue.ProcessInstanceModificationTerminateInstructionValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
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
public final class ProcessInstanceModificationTerminateInstruction
    extends MsgpackBufferValue<ProcessInstanceModificationTerminateInstruction>
    implements ProcessInstanceModificationTerminateInstructionValue {

  long elementInstanceKey = -1L;
  DirectBuffer elementId = Decoding.EMPTY;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public long getElementInstanceKey() {
    return elementInstanceKey;
  }

  @Override
  public String getElementId() {
    return BufferUtil.bufferAsString(elementId);
  }

  public ProcessInstanceModificationTerminateInstruction setElementId(final String elementId) {
    this.elementId = BufferUtil.wrapString(elementId);
    return this;
  }

  public ProcessInstanceModificationTerminateInstruction setElementInstanceKey(
      final long elementInstanceKey) {
    this.elementInstanceKey = elementInstanceKey;
    return this;
  }

  @JsonIgnore
  public DirectBuffer getElementIdBuffer() {
    return elementId;
  }

  public void copy(final ProcessInstanceModificationTerminateInstructionValue object) {
    setElementInstanceKey(object.getElementInstanceKey());
    setElementId(object.getElementId());
  }

  @Override
  protected MsgpackCodec<ProcessInstanceModificationTerminateInstruction> codec() {
    return ProcessInstanceModificationTerminateInstructionCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<ProcessInstanceModificationTerminateInstruction> ops() {
    return ProcessInstanceModificationTerminateInstructionOps.INSTANCE;
  }
}
