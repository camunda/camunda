/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.db.impl;

import static io.camunda.zeebe.db.impl.ZeebeDbConstants.ZB_DB_BYTE_ORDER;

import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import java.util.Objects;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class DbString implements DbKey, DbValue {

  private final DirectBuffer bytes = new UnsafeBuffer(0, 0);

  public void wrapString(final String string) {
    bytes.wrap(string.getBytes());
  }

  public void wrapBuffer(final DirectBuffer buffer) {
    bytes.wrap(buffer);
  }

  @Override
  public void wrap(final DirectBuffer directBuffer, int offset, final int length) {
    final int stringLen = directBuffer.getInt(offset, ZB_DB_BYTE_ORDER);
    offset += Integer.BYTES;

    final byte[] b = new byte[stringLen];
    directBuffer.getBytes(offset, b);
    bytes.wrap(b);
  }

  @Override
  public int getLength() {
    return Integer.BYTES // length of the string
        + bytes.capacity();
  }

  @Override
  public void write(final MutableDirectBuffer mutableDirectBuffer, int offset) {
    final int length = bytes.capacity();
    mutableDirectBuffer.putInt(offset, length, ZB_DB_BYTE_ORDER);
    offset += Integer.BYTES;

    mutableDirectBuffer.putBytes(offset, bytes, 0, bytes.capacity());
  }

  @Override
  public String toString() {
    return BufferUtil.bufferAsString(bytes);
  }

  public DirectBuffer getBuffer() {
    return bytes;
  }

  @Override
  public int hashCode() {
    return bytes.hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (getClass() != obj.getClass()) {
      return false;
    }
    final var other = (DbString) obj;
    return Objects.equals(bytes, other.bytes);
  }
}
