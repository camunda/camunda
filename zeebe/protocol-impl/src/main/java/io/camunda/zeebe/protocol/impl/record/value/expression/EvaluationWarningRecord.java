/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.protocol.impl.record.value.expression;

import static io.camunda.zeebe.util.buffer.BufferUtil.bufferAsString;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.msgpack.value.StringValue;

public final class EvaluationWarningRecord extends UnpackedObject {

  private static final StringValue TYPE_KEY = new StringValue("type");
  private static final StringValue MESSAGE_KEY = new StringValue("message");

  private final StringProperty typeProp = new StringProperty(TYPE_KEY);
  private final StringProperty messageProp = new StringProperty(MESSAGE_KEY);

  public EvaluationWarningRecord() {
    super(2);
    declareProperty(typeProp).declareProperty(messageProp);
  }

  public void wrap(final EvaluationWarningRecord warning) {
    setType(warning.getType()).setMessage(warning.getMessage());
  }

  public String getType() {
    return bufferAsString(typeProp.getValue());
  }

  public EvaluationWarningRecord setType(final String type) {
    typeProp.setValue(type);
    return this;
  }

  public String getMessage() {
    return bufferAsString(messageProp.getValue());
  }

  public EvaluationWarningRecord setMessage(final String message) {
    messageProp.setValue(message);
    return this;
  }
}
