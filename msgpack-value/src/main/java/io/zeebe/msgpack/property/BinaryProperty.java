/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.msgpack.property;

import io.zeebe.msgpack.value.BinaryValue;
import org.agrona.DirectBuffer;

public class BinaryProperty extends BaseProperty<BinaryValue> {
  public BinaryProperty(String keyString) {
    super(keyString, new BinaryValue());
  }

  public BinaryProperty(String keyString, DirectBuffer defaultValue) {
    super(keyString, new BinaryValue(), new BinaryValue(defaultValue, 0, defaultValue.capacity()));
  }

  public DirectBuffer getValue() {
    return resolveValue().getValue();
  }

  public void setValue(DirectBuffer data) {
    setValue(data, 0, data.capacity());
  }

  public void setValue(DirectBuffer data, int offset, int length) {
    this.value.wrap(data, offset, length);
    this.isSet = true;
  }
}
