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
import io.camunda.zeebe.msgpack.property.ArrayProperty;
import io.camunda.zeebe.msgpack.property.BinaryProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.StringValue;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.EvaluatedInputValue;
import io.camunda.zeebe.protocol.record.value.ProtectionMode;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Set;
import java.util.stream.Collectors;
import org.agrona.DirectBuffer;

public final class EvaluatedInputRecord extends UnifiedRecordValue implements EvaluatedInputValue {

  private static final StringValue PROTECTION_MODES_KEY = new StringValue("protectionModes");

  private final StringProperty inputIdProp = new StringProperty("inputId");
  private final StringProperty inputNameProp = new StringProperty("inputName", "");
  private final BinaryProperty inputValueProp = new BinaryProperty("inputValue");
  private final ArrayProperty<StringValue> protectionModesProp =
      new ArrayProperty<>(PROTECTION_MODES_KEY, StringValue::new);

  public EvaluatedInputRecord() {
    super(4);
    declareProperty(inputIdProp)
        .declareProperty(inputNameProp)
        .declareProperty(inputValueProp)
        .declareProperty(protectionModesProp);
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

  @Override
  public Set<ProtectionMode> getProtectionModes() {
    return protectionModesProp.stream()
        .map(StringValue::getValue)
        .map(BufferUtil::bufferAsString)
        .map(ProtectionMode::valueOf)
        .collect(Collectors.toSet());
  }

  public EvaluatedInputRecord setProtectionModes(final Set<ProtectionMode> protectionModes) {
    protectionModesProp.reset();
    protectionModes.forEach(
        mode -> protectionModesProp.add().wrap(BufferUtil.wrapString(mode.name())));
    return this;
  }

  // Derived from getProtectionModes(); excluded from JSON to avoid duplicating that field.
  @JsonIgnore
  @Override
  public boolean shouldBeRedacted() {
    return EvaluatedInputValue.super.shouldBeRedacted();
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
