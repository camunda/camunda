/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.decision;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.pojo.Decoding;
import io.camunda.zeebe.msgpack.pojo.MsgpackBufferValue;
import io.camunda.zeebe.msgpack.pojo.MsgpackCodec;
import io.camunda.zeebe.msgpack.pojo.MsgpackOps;
import io.camunda.zeebe.msgpack.pojo.MsgpackPojo;
import io.camunda.zeebe.msgpack.pojo.MsgpackProperty;
import io.camunda.zeebe.msgpack.pojo.MsgpackUnknownProperties;
import io.camunda.zeebe.msgpack.pojo.UnknownProperties;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.MsgpackRecordValue;
import io.camunda.zeebe.protocol.record.value.EvaluatedInputValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@MsgpackPojo
@NullMarked
public final class EvaluatedInputRecord extends MsgpackBufferValue<EvaluatedInputRecord>
    implements MsgpackRecordValue, EvaluatedInputValue {

  @Nullable DirectBuffer inputId;
  DirectBuffer inputName = Decoding.EMPTY;

  @MsgpackProperty(binary = true)
  @Nullable DirectBuffer inputValue;

  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public String getInputId() {
    return bufferAsString(Decoding.required(inputId, "inputId"));
  }

  public EvaluatedInputRecord setInputId(final String inputId) {
    this.inputId = BufferUtil.wrapString(inputId);
    return this;
  }

  @Override
  public String getInputName() {
    return bufferAsString(inputName);
  }

  public EvaluatedInputRecord setInputName(final String inputName) {
    this.inputName = BufferUtil.wrapString(inputName);
    return this;
  }

  @Override
  public String getInputValue() {
    return MsgPackConverter.convertToJson(Decoding.required(inputValue, "inputValue"));
  }

  public EvaluatedInputRecord setInputValue(final DirectBuffer inputValue) {
    this.inputValue = inputValue;
    return this;
  }

  @JsonIgnore
  public DirectBuffer getInputValueBuffer() {
    return Decoding.required(inputValue, "inputValue");
  }

  @JsonIgnore
  public DirectBuffer getInputIdBuffer() {
    return Decoding.required(inputId, "inputId");
  }

  @JsonIgnore
  public DirectBuffer getInputNameBuffer() {
    return inputName;
  }

  @Override
  protected MsgpackCodec<EvaluatedInputRecord> codec() {
    return EvaluatedInputRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<EvaluatedInputRecord> ops() {
    return EvaluatedInputRecordOps.INSTANCE;
  }
}
