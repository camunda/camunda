/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.events;

import io.camunda.zeebe.msgpack.property.LongProperty;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.protocol.impl.record.UnifiedRecordValue;
import io.camunda.zeebe.util.buffer.BufferUtil;

public class CatchEventRecord extends UnifiedRecordValue {

  private final LongProperty scopeKeyProp = new LongProperty("scopeKey", -1L);
  private final StringProperty errorCodeProp = new StringProperty("errorCode", "");
  private final StringProperty errorMessageProp = new StringProperty("errorMessage", "");

  public CatchEventRecord() {
    super(3);
    declareProperty(scopeKeyProp).declareProperty(errorCodeProp).declareProperty(errorMessageProp);
  }

  public void wrap(final CatchEventRecord other) {
    scopeKeyProp.setValue(other.getScopeKey());
    errorCodeProp.setValue(other.getErrorCode());
    errorMessageProp.setValue(other.getErrorMessage());
  }

  public long getScopeKey() {
    return scopeKeyProp.getValue();
  }

  public CatchEventRecord setScopeKey(final long scopeKey) {
    scopeKeyProp.setValue(scopeKey);
    return this;
  }

  public String getErrorCode() {
    return BufferUtil.bufferAsString(errorCodeProp.getValue());
  }

  public CatchEventRecord setErrorCode(final String errorCode) {
    errorCodeProp.setValue(errorCode);
    return this;
  }

  public String getErrorMessage() {
    return BufferUtil.bufferAsString(errorMessageProp.getValue());
  }

  public CatchEventRecord setErrorMessage(final String errorMessage) {
    errorMessageProp.setValue(errorMessage);
    return this;
  }
}
