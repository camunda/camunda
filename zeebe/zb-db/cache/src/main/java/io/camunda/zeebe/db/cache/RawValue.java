/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.cache;

import io.camunda.zeebe.db.DbValue;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;

/**
 * The value type of the backend's pass-through column families. It writes its bytes unchanged, so
 * the backend stores exactly what the caller's value serialized to.
 *
 * <p>{@link #wrap} copies what the backend reads into a new array, because the backend reuses its
 * buffers.
 */
final class RawValue implements DbValue {

  private byte[] bytes = new byte[0];

  /** Uses {@code bytes} without copying; the caller must not mutate them afterwards. */
  void set(final byte[] bytes) {
    this.bytes = bytes;
  }

  byte[] bytes() {
    return bytes;
  }

  @Override
  public void wrap(final DirectBuffer buffer, final int offset, final int length) {
    bytes = new byte[length];
    buffer.getBytes(offset, bytes);
  }

  @Override
  public int getLength() {
    return bytes.length;
  }

  @Override
  public int write(final MutableDirectBuffer buffer, final int offset) {
    buffer.putBytes(offset, bytes);
    return bytes.length;
  }
}
