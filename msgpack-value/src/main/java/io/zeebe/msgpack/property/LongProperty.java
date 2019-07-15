/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.msgpack.property;

import io.zeebe.msgpack.value.LongValue;

public class LongProperty extends BaseProperty<LongValue> {
  public LongProperty(String key) {
    super(key, new LongValue());
  }

  public LongProperty(String key, long defaultValue) {
    super(key, new LongValue(), new LongValue(defaultValue));
  }

  public long getValue() {
    return resolveValue().getValue();
  }

  public void setValue(long value) {
    this.value.setValue(value);
    this.isSet = true;
  }
}
