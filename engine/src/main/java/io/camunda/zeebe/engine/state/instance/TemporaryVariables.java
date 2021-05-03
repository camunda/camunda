/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.engine.state.instance;

import io.zeebe.db.DbValue;
import io.zeebe.msgpack.UnpackedObject;
import io.zeebe.msgpack.property.BinaryProperty;
import org.agrona.DirectBuffer;

public class TemporaryVariables extends UnpackedObject implements DbValue {
  private final BinaryProperty valueProp = new BinaryProperty("temporaryVariables");

  public TemporaryVariables() {
    declareProperty(valueProp);
  }

  public DirectBuffer get() {
    return valueProp.getValue();
  }

  public void set(final DirectBuffer value) {
    valueProp.setValue(value);
  }
}
