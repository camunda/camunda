/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.msgpack;

import io.zeebe.msgpack.property.LongProperty;

public class POJONested extends UnpackedObject {
  private final LongProperty longProp = new LongProperty("foo", -1L);

  public POJONested() {
    this.declareProperty(longProp);
  }

  public long getLong() {
    return this.longProp.getValue();
  }

  public POJONested setLong(long value) {
    this.longProp.setValue(value);
    return this;
  }
}
