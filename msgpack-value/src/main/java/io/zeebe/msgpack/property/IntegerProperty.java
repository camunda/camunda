/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.msgpack.property;

import io.zeebe.msgpack.value.IntegerValue;

public class IntegerProperty extends BaseProperty<IntegerValue> {
  public IntegerProperty(String key) {
    super(key, new IntegerValue());
  }

  public IntegerProperty(String key, int defaultValue) {
    super(key, new IntegerValue(), new IntegerValue(defaultValue));
  }

  public int getValue() {
    return resolveValue().getValue();
  }

  public void setValue(int value) {
    this.value.setValue(value);
    this.isSet = true;
  }
}
