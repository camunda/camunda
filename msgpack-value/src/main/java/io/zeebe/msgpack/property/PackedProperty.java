/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.msgpack.property;

import io.zeebe.msgpack.value.PackedValue;
import org.agrona.DirectBuffer;

public class PackedProperty extends BaseProperty<PackedValue> {
  public PackedProperty(final String key) {
    super(key, new PackedValue());
  }

  public PackedProperty(final String key, final DirectBuffer defaultValue) {
    super(key, new PackedValue(), new PackedValue(defaultValue, 0, defaultValue.capacity()));
  }

  public DirectBuffer getValue() {
    return resolveValue().getValue();
  }

  public void setValue(final DirectBuffer buffer, final int offset, final int length) {
    value.wrap(buffer, offset, length);
    isSet = true;
  }
}
