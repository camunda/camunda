/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.adhocsubprocess;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackProperty;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.msgpack.value.DocumentValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.record.value.AdHocSubProcessInstructionRecordValue.AdHocSubProcessActivateElementInstructionValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Map;
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
public final class AdHocSubProcessActivateElementInstruction
    extends MsgpackBufferValue<AdHocSubProcessActivateElementInstruction>
    implements AdHocSubProcessActivateElementInstructionValue {

  @Nullable DirectBuffer elementId;

  @MsgpackProperty(document = true)
  DirectBuffer variables = DocumentValue.EMPTY_DOCUMENT;

  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public String getElementId() {
    return BufferUtil.bufferAsString(Decoding.required(elementId, "elementId"));
  }

  public AdHocSubProcessActivateElementInstruction setElementId(final String elementId) {
    this.elementId = BufferUtil.wrapString(elementId);
    return this;
  }

  @Override
  public Map<String, Object> getVariables() {
    return MsgPackConverter.convertToMap(variables);
  }

  public AdHocSubProcessActivateElementInstruction setVariables(final DirectBuffer variables) {
    this.variables = Decoding.document(variables);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getVariablesBuffer() {
    return variables;
  }

  @Override
  protected MsgpackCodec<AdHocSubProcessActivateElementInstruction> codec() {
    return AdHocSubProcessActivateElementInstructionCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<AdHocSubProcessActivateElementInstruction> ops() {
    return AdHocSubProcessActivateElementInstructionOps.INSTANCE;
  }
}
