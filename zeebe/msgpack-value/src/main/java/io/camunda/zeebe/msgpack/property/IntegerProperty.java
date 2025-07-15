/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.property;

import io.camunda.zeebe.msgpack.value.IntegerValue;
import io.camunda.zeebe.msgpack.value.StringValue;

public final class IntegerProperty extends BaseProperty<IntegerValue> {
  public IntegerProperty(final String key) {
    super(key, new IntegerValue());
  }

  public IntegerProperty(final String key, final int defaultValue) {
    super(key, new IntegerValue(), new IntegerValue(defaultValue));
  }

  public IntegerProperty(final StringValue key) {
    super(key, new IntegerValue());
  }

  public IntegerProperty(final StringValue key, final int defaultValue) {
    super(key, new IntegerValue(), new IntegerValue(defaultValue));
  }

  public int getValue() {
    return resolveValue().getValue();
  }

  public void setValue(final int value) {
    this.value.setValue(value);
    isSet = true;
  }

  public int decrement() {
    final int decrValue = getValue() - 1;
    setValue(decrValue);
    return decrValue;
  }

  public int increment() {
    final int incrValue = getValue() + 1;
    setValue(incrValue);
    return incrValue;
  }
}
