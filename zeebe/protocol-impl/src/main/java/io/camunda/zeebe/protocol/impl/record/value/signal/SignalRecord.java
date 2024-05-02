/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.signal;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.zeebe.msgpack.property.DocumentProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.encoding.MsgPackConverter;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.protocol.record.value.SignalRecordValue;
import io.camunda.zeebe.protocol.record.value.TenantOwned;
import java.util.Map;
import org.agrona.DirectBuffer;

public final class SignalRecord extends UnifiedRecordValue implements SignalRecordValue {

  private final StringProperty signalNameProp = new StringProperty("signalName");
  private final DocumentProperty variablesProp = new DocumentProperty("variables");
  private final StringProperty tenantIdProp =
      new StringProperty("tenantId", TenantOwned.DEFAULT_TENANT_IDENTIFIER);

  public SignalRecord() {
    super(3);
    declareProperty(signalNameProp).declareProperty(variablesProp).declareProperty(tenantIdProp);
  }

  public void wrap(final SignalRecord record) {
    setSignalName(record.getSignalNameBuffer())
        .setVariables(record.getVariablesBuffer())
        .setTenantId(record.getTenantId());
  }

  @Override
  public String getSignalName() {
    return bufferAsString(signalNameProp.getValue());
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
    return bufferAsString(tenantIdProp.getValue());
  }

  public SignalRecord setTenantId(final String tenantId) {
    tenantIdProp.setValue(tenantId);
    return this;
  }
}
