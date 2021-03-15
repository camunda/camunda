/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.zeebe.msgpack.property;

import io.zeebe.msgpack.value.BinaryValue;
import org.agrona.DirectBuffer;

public final class BinaryProperty extends BaseProperty<BinaryValue> {
  public BinaryProperty(final String keyString) {
    super(keyString, new BinaryValue());
  }

  public BinaryProperty(final String keyString, final DirectBuffer defaultValue) {
    super(keyString, new BinaryValue(), new BinaryValue(defaultValue, 0, defaultValue.capacity()));
  }

  public DirectBuffer getValue() {
    return resolveValue().getValue();
  }

  public void setValue(final DirectBuffer data) {
    setValue(data, 0, data.capacity());
  }

  public void setValue(final DirectBuffer data, final int offset, final int length) {
    value.wrap(data, offset, length);
    isSet = true;
  }
}
