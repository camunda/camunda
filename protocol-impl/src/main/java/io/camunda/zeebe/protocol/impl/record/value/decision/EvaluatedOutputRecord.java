/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.impl.record.value.decision;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.BinaryProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.EvaluatedOutputValue;
import org.agrona.DirectBuffer;

public final class EvaluatedOutputRecord extends UnifiedRecordValue
    implements EvaluatedOutputValue {

  private final StringProperty outputIdProp = new StringProperty("outputId");
  private final StringProperty outputNameProp = new StringProperty("outputName", "");
  private final BinaryProperty outputValueProp = new BinaryProperty("outputValue");

  public EvaluatedOutputRecord() {
    declareProperty(outputIdProp).declareProperty(outputNameProp).declareProperty(outputValueProp);
  }

  @Override
  public String getOutputId() {
    return bufferAsString(outputIdProp.getValue());
  }

  public EvaluatedOutputRecord setOutputId(final String outputId) {
    outputIdProp.setValue(outputId);
    return this;
  }

  @Override
  public String getOutputName() {
    return bufferAsString(outputNameProp.getValue());
  }

  public EvaluatedOutputRecord setOutputName(final String outputName) {
    outputNameProp.setValue(outputName);
    return this;
  }

  @Override
  public String getOutputValue() {
    return MsgPackConverter.convertToJson(outputValueProp.getValue());
  }

  public EvaluatedOutputRecord setOutputValue(final DirectBuffer outputValue) {
    outputValueProp.setValue(outputValue);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getOutputValueBuffer() {
    return outputValueProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getOutputIdBuffer() {
    return outputIdProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getOutputNameBuffer() {
    return outputNameProp.getValue();
  }
}
