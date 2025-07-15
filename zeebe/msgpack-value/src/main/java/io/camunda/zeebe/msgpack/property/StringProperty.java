/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.msgpack.property;

import static io.camunda.zeebe.util.StringUtil.getBytes;

import io.camunda.zeebe.msgpack.value.StringValue;
import org.agrona.DirectBuffer;

public final class StringProperty extends BaseProperty<StringValue> {

  public StringProperty(final String key) {
    super(key, new StringValue());
  }

  public StringProperty(final String key, final String defaultValue) {
    super(key, new StringValue(), new StringValue(defaultValue));
  }

  public StringProperty(final StringValue key) {
    super(key, new StringValue());
  }

  public StringProperty(final StringValue key, final String defaultValue) {
    super(key, new StringValue(), new StringValue(defaultValue));
  }

  public DirectBuffer getValue() {
    return resolveValue().getValue();
  }

  public void setValue(final String value) {
    this.value.wrap(getBytes(value));
    isSet = true;
  }

  public void setValue(final DirectBuffer buffer) {
    setValue(buffer, 0, buffer.capacity());
  }

  public void setValue(final DirectBuffer buffer, final int offset, final int length) {
    value.wrap(buffer, offset, length);
    isSet = true;
  }
}
