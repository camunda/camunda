/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.protocol.impl.record.value.signal;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.DocumentProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.SignalRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Map;
import org.agrona.DirectBuffer;

public final class SignalRecord extends UnifiedRecordValue implements SignalRecordValue {

  private final StringProperty signalNameProp = new StringProperty("signalName");

  private final DocumentProperty variablesProp = new DocumentProperty("variables");

  public SignalRecord() {
    super(2);
    declareProperty(signalNameProp).declareProperty(variablesProp);
  }

  public void wrap(final SignalRecord record) {
    setSignalName(record.getSignalNameBuffer());
    setVariables(record.getVariablesBuffer());
  }

  @Override
  public String getSignalName() {
    return BufferUtil.bufferAsString(signalNameProp.getValue());
  }

  public SignalRecord setSignalName(final String signalName) {
    signalNameProp.setValue(signalName);
    return this;
  }

  public SignalRecord setSignalName(final DirectBuffer signalName) {
    signalNameProp.setValue(signalName);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getSignalNameBuffer() {
    return signalNameProp.getValue();
  }

  @Override
  public Map<String, Object> getVariables() {
    return MsgPackConverter.convertToMap(variablesProp.getValue());
  }

  public SignalRecord setVariables(final DirectBuffer variables) {
    variablesProp.setValue(variables);
    return this;
  }

  @JsonIgnore
  public DirectBuffer getVariablesBuffer() {
    return variablesProp.getValue();
  }

  @Override
  public String getTenantId() {
    // todo(#13336): replace dummy implementation
    return TenantOwned.DEFAULT_TENANT_IDENTIFIER;
  }
}
