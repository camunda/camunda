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
import io.camunda.zeebe.protocol.record.value.EvaluatedOutputValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@MsgpackPojo
@NullMarked
public final class EvaluatedOutputRecord extends MsgpackBufferValue<EvaluatedOutputRecord>
    implements MsgpackRecordValue, EvaluatedOutputValue {

  @Nullable DirectBuffer outputId;
  DirectBuffer outputName = Decoding.EMPTY;

  @MsgpackProperty(binary = true)
  @Nullable DirectBuffer outputValue;

  @MsgpackUnknownProperties final UnknownProperties unknownProperties = new UnknownProperties();

  @Override
  public String getOutputId() {
    return bufferAsString(Decoding.required(outputId, "outputId"));
  }

  public EvaluatedOutputRecord setOutputId(final String outputId) {
    this.outputId = BufferUtil.wrapString(outputId);
    return this;
  }

  @Override
  public String getOutputName() {
    return bufferAsString(outputName);
  }

  public EvaluatedOutputRecord setOutputName(final String outputName) {
    this.outputName = BufferUtil.wrapString(outputName);
    return this;
  }

  @Override
  public String getOutputValue() {
    return MsgPackConverter.convertToJson(Decoding.required(outputValue, "outputValue"));
  }

  public EvaluatedOutputRecord setOutputValue(final DirectBuffer outputValue) {
    this.outputValue = outputValue;
    return this;
  }

  @JsonIgnore
  public DirectBuffer getOutputValueBuffer() {
    return Decoding.required(outputValue, "outputValue");
  }

  @JsonIgnore
  public DirectBuffer getOutputIdBuffer() {
    return Decoding.required(outputId, "outputId");
  }

  @JsonIgnore
  public DirectBuffer getOutputNameBuffer() {
    return outputName;
  }

  @Override
  protected MsgpackCodec<EvaluatedOutputRecord> codec() {
    return EvaluatedOutputRecordCodec.INSTANCE;
  }

  @Override
  protected MsgpackOps<EvaluatedOutputRecord> ops() {
    return EvaluatedOutputRecordOps.INSTANCE;
  }
}
