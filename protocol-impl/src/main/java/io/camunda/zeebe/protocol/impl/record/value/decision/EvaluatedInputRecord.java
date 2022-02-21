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
import io.camunda.zeebe.protocol.record.value.EvaluatedInputValue;
import org.agrona.DirectBuffer;

public final class EvaluatedInputRecord extends UnifiedRecordValue implements EvaluatedInputValue {

  private final StringProperty inputIdProp = new StringProperty("inputId");
  private final StringProperty inputNameProp = new StringProperty("inputName");
  private final BinaryProperty inputValueProp = new BinaryProperty("inputValue");

  public EvaluatedInputRecord() {
    declareProperty(inputIdProp).declareProperty(inputNameProp).declareProperty(inputValueProp);
  }

  @Override
  public String getInputId() {
    return bufferAsString(inputIdProp.getValue());
  }

  public EvaluatedInputRecord setInputId(final String inputId) {
    inputIdProp.setValue(inputId);
    return this;
  }

  @Override
  public String getInputName() {
    return bufferAsString(inputNameProp.getValue());
  }

  public EvaluatedInputRecord setInputName(final String inputName) {
    inputNameProp.setValue(inputName);
    return this;
  }

  @Override
  public String getInputValue() {
    return MsgPackConverter.convertToJson(inputValueProp.getValue());
  }

  public EvaluatedInputRecord setInputValue(final DirectBuffer inputValue) {
    inputValueProp.setValue(inputValue);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getInputValueBuffer() {
    return inputValueProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getInputIdBuffer() {
    return inputIdProp.getValue();
  }

  @JsonIgnore
  public DirectBuffer getInputNameBuffer() {
    return inputNameProp.getValue();
  }
}
