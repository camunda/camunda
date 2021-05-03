/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.msgpack;

import io.camunda.zeebe.msgpack.property.LongProperty;

public final class POJONested extends UnpackedObject {
  private final LongProperty longProp = new LongProperty("foo", -1L);

  public POJONested() {
    declareProperty(longProp);
  }

  public long getLong() {
    return longProp.getValue();
  }

  public POJONested setLong(final long value) {
    longProp.setValue(value);
    return this;
  }
}
