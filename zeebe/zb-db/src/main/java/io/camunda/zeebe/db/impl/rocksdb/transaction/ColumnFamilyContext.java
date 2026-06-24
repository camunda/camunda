/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.rocksdb.transaction;

import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import io.camunda.zeebe.db.impl.ZeebeDbConstants;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.ObjIntConsumer;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class ColumnFamilyContext {

  private static final byte[] ZERO_SIZE_ARRAY = new byte[0];

  // we can also simply use one buffer
  private final ExpandableArrayBuffer keyBuffer = new ExpandableArrayBuffer();
  private final ExpandableArrayBuffer valueBuffer = new ExpandableArrayBuffer();

  private final DirectBuffer keyViewBuffer = new UnsafeBuffer(0, 0);
  private final DirectBuffer valueViewBuffer = new UnsafeBuffer(0, 0);

  private final Queue<ExpandableArrayBuffer> prefixKeyBuffers;
  private int keyLength;
  private final long columnFamilyPrefix;

  public ColumnFamilyContext(final long columnFamilyPrefix) {
    this.columnFamilyPrefix = columnFamilyPrefix;
    prefixKeyBuffers = new ArrayDeque<>();
    prefixKeyBuffers.add(new ExpandableArrayBuffer());
    prefixKeyBuffers.add(new ExpandableArrayBuffer());
  }

  public void writeKey(final DbKey key) {
    keyLength = 0;
    keyBuffer.putLong(0, columnFamilyPrefix, ZeebeDbConstants.ZB_DB_BYTE_ORDER);
    keyLength += Long.BYTES;
    key.write(keyBuffer, Long.BYTES);
    keyLength += key.getLength();
  }

  public int getKeyLength() {
    return keyLength;
  }

  public byte[] getKeyBufferArray() {
    return keyBuffer.byteArray();
  }

  public int writeValue(final DbValue value) {
    return value.write(valueBuffer, 0);
  }

  public byte[] getValueBufferArray() {
    return valueBuffer.byteArray();
  }

  public void wrapKeyView(final byte[] key) {
    if (key != null) {
      // wrap without the column family key
      keyViewBuffer.wrap(key, Long.BYTES, key.length - Long.BYTES);
    } else {
      keyViewBuffer.wrap(ZERO_SIZE_ARRAY);
    }
  }

  public DirectBuffer getKeyView() {
    return isKeyViewEmpty() ? null : keyViewBuffer;
  }

  public boolean isKeyViewEmpty() {
    return keyViewBuffer.capacity() == ZERO_SIZE_ARRAY.length;
  }

  public void wrapValueView(final byte[] value) {
    if (value != null) {
      valueViewBuffer.wrap(value);
    } else {
      valueViewBuffer.wrap(ZERO_SIZE_ARRAY);
    }
  }

  public DirectBuffer getValueView() {
    return isValueViewEmpty() ? null : valueViewBuffer;
  }

  public boolean isValueViewEmpty() {
    return valueViewBuffer.capacity() == ZERO_SIZE_ARRAY.length;
  }

  public void withPrefixKey(final DbKey key, final ObjIntConsumer<byte[]> prefixKeyConsumer) {
    if (prefixKeyBuffers.peek() == null) {
      throw new IllegalStateException(
          "Currently nested prefix iterations are not supported! This will cause unexpected behavior.");
    }

    final ExpandableArrayBuffer prefixKeyBuffer = prefixKeyBuffers.remove();
    try {
      prefixKeyBuffer.putLong(0, columnFamilyPrefix, ZeebeDbConstants.ZB_DB_BYTE_ORDER);
      key.write(prefixKeyBuffer, Long.BYTES);
      final int prefixLength = Long.BYTES + key.getLength();

      prefixKeyConsumer.accept(prefixKeyBuffer.byteArray(), prefixLength);
    } finally {
      prefixKeyBuffers.add(prefixKeyBuffer);
    }
  }

  ByteBuffer keyWithColumnFamily(final DbKey key) {
    final var bytes = ByteBuffer.allocate(Long.BYTES + key.getLength());
    final var buffer = new UnsafeBuffer(bytes);

    buffer.putLong(0, columnFamilyPrefix, ZeebeDbConstants.ZB_DB_BYTE_ORDER);
    key.write(buffer, Long.BYTES);
    return bytes;
  }
}
