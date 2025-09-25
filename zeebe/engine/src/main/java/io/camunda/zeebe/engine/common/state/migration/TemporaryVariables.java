/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.common.state.migration;

import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.BinaryProperty;
import org.agrona.DirectBuffer;

public class TemporaryVariables extends UnpackedObject implements DbValue {
  private final BinaryProperty valueProp = new BinaryProperty("temporaryVariables");

  public TemporaryVariables() {
    super(1);
    declareProperty(valueProp);
  }

  public DirectBuffer get() {
    return valueProp.getValue();
  }

  public void set(final DirectBuffer value) {
    valueProp.setValue(value);
  }
}
