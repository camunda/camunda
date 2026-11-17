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
import io.camunda.zeebe.protocol.record.value.ProcessInstanceCreationRecordValue.ProcessInstanceCreationStartInstructionValue;
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
public final class ProcessInstanceCreationStartInstruction
    extends MsgpackBufferValue<ProcessInstanceCreationStartInstruction>
    implements ProcessInstanceCreationStartInstructionValue {

  @Nullable DirectBuffer elementId;
  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @JsonIgnore
  public DirectBuffer getElementIdBuffer() {
    return Decoding.required(elementId, "elementId");
  }

  @Override
  public String getElementId() {
    return BufferUtil.bufferAsString(getElementIdBuffer());
  }

  public ProcessInstanceCreationStartInstruction setElementId(final String elementId) {
    this.elementId = BufferUtil.wrapString(elementId);
    return this;
  }

  public void copy(final ProcessInstanceCreationStartInstructionValue startInstruction) {
    setElementId(startInstruction.getElementId());
  }

  @Override
  protected MsgpackCodec<ProcessInstanceCreationStartInstruction> codec() {
    return ProcessInstanceCreationStartInstructionCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<ProcessInstanceCreationStartInstruction> ops() {
    return ProcessInstanceCreationStartInstructionOps.INSTANCE;
  }
}
