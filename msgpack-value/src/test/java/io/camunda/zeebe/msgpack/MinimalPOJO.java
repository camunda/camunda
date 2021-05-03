/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.msgpack;

import io.zeebe.msgpack.property.LongProperty;

public final class MinimalPOJO extends UnpackedObject {

  private final LongProperty longProp = new LongProperty("longProp");

  public MinimalPOJO() {
    declareProperty(longProp);
  }

  public long getLongProp() {
    return longProp.getValue();
  }

  public void setLongProp(final long value) {
    longProp.setValue(value);
  }
}
