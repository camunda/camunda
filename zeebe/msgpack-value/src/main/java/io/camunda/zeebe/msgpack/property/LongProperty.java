/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.property;

import io.camunda.zeebe.msgpack.value.LongValue;
import io.camunda.zeebe.msgpack.value.StringValue;

public final class LongProperty extends BaseProperty<LongValue> {
  public LongProperty(final String key) {
    super(key, new LongValue());
  }

  public LongProperty(final String key, final long defaultValue) {
    super(key, new LongValue(), new LongValue(defaultValue));
  }

  public LongProperty(final StringValue key) {
    super(key, new LongValue());
  }

  public LongProperty(final StringValue key, final long defaultValue) {
    super(key, new LongValue(), new LongValue(defaultValue));
  }

  public long getValue() {
    return resolveValue().getValue();
  }

  public void setValue(final long value) {
    this.value.setValue(value);
    isSet = true;
  }
}
