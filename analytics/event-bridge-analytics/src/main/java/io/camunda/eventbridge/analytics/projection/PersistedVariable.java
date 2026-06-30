/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.analytics.projection;

import io.camunda.zeebe.msgpack.UnpackedObject;
import io.camunda.zeebe.msgpack.property.StringProperty;
import io.camunda.zeebe.util.buffer.BufferUtil;

/** One process-instance variable (name → value) stored in the persisted projection's array. */
public final class PersistedVariable extends UnpackedObject {

  private final StringProperty nameProp = new StringProperty("name", "");
  private final StringProperty valueProp = new StringProperty("value", "");

  public PersistedVariable() {
    super(2);
    declareProperty(nameProp).declareProperty(valueProp);
  }

  public PersistedVariable set(final String name, final String value) {
    nameProp.setValue(name);
    valueProp.setValue(value);
    return this;
  }

  public String name() {
    return BufferUtil.bufferAsString(nameProp.getValue());
  }

  public String value() {
    return BufferUtil.bufferAsString(valueProp.getValue());
  }
}
