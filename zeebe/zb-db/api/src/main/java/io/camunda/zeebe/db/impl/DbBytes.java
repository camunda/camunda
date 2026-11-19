/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl;

import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.util.buffer.BufferUtil;
import org.agrona.DirectBuffer;
import org.agrona.MutableDirectBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public final class DbBytes implements DbKey, DbValue {

  private final DirectBuffer bytes = new UnsafeBuffer(0, 0);

  public void wrapBytes(final byte[] value) {
    bytes.wrap(value);
  }

  @Override
  public void wrap(final DirectBuffer directBuffer, final int offset, final int length) {
    final byte[] bytesToWrap = new byte[length];
    directBuffer.getBytes(offset, bytesToWrap, 0, bytesToWrap.length);
    bytes.wrap(bytesToWrap);
  }

  @Override
  public int getLength() {
    return bytes.capacity();
  }

  @Override
  public int write(final MutableDirectBuffer mutableDirectBuffer, final int offset) {
    mutableDirectBuffer.putBytes(offset, bytes, 0, bytes.capacity());
    return getLength();
  }

  public byte[] getBytes() {
    return bytes.byteArray();
  }

  @Override
  public String toString() {
    return "DbByte{" + "bytes=" + BufferUtil.bufferAsString(bytes) + '}';
  }

  public DirectBuffer getDirectBuffer() {
    return bytes;
  }
}
