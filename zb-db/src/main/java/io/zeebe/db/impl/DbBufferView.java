/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.0. You may not use this file
 * except in compliance with the Zeebe Community License 1.0.
 */
package io.zeebe.db.impl;

import io.zeebe.db.DbValue;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class DbBufferView implements DbValue {

  private final MutableDirectBuffer value = new UnsafeBuffer(0, 0);

  public void wrapBuffer(final DirectBuffer buffer, final int offset, final int length) {
    value.wrap(buffer, offset, length);
  }

  public void wrapBuffer(final DirectBuffer buffer) {
    value.wrap(buffer);
  }

  public DirectBuffer getValue() {
    return value;
  }

  @Override
  public int getLength() {
    return value.capacity();
  }

  @Override
  public void write(final MutableDirectBuffer buffer, final int offset) {
    buffer.putBytes(offset, value, 0, value.capacity());
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    value.wrap(buffer, offset, length);
  }
}
