/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.db.impl.rocksdb.transaction;

import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.DbValue;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.function.ObjIntConsumer;
import org.agrona.DirectBuffer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;

public class ColumnFamilyContext {

  private static final byte[] ZERO_SIZE_ARRAY = new byte[0];

  private final ColumnFamilyKey columnFamilyKey;

  // we can also simply use one buffer
  private final ExpandableArrayBuffer keyBuffer = new ExpandableArrayBuffer();
  private final ExpandableArrayBuffer valueBuffer = new ExpandableArrayBuffer();

  private final DirectBuffer keyViewBuffer = new UnsafeBuffer(0, 0);
  private final DirectBuffer valueViewBuffer = new UnsafeBuffer(0, 0);

  private final Queue<ExpandableArrayBuffer> prefixKeyBuffers;

  private int keyLength;

  ColumnFamilyContext(final ColumnFamilyKey columnFamilyKey) {
    this.columnFamilyKey = columnFamilyKey;
    prefixKeyBuffers = new ArrayDeque<>();
    prefixKeyBuffers.add(new ExpandableArrayBuffer());
    prefixKeyBuffers.add(new ExpandableArrayBuffer());
  }

  public void writeKey(final DbKey key) {
    final int columnFamilyKeyLength;
    int keyLength = 0;
    int offset = 0;

    columnFamilyKey.write(keyBuffer, offset);
    columnFamilyKeyLength = columnFamilyKey.getLength();
    keyLength += columnFamilyKeyLength;
    offset += columnFamilyKeyLength;

    key.write(keyBuffer, offset);
    keyLength += key.getLength();

    this.keyLength = keyLength;
  }

  public int getKeyLength() {
    return keyLength;
  }

  public byte[] getKeyBufferArray() {
    return keyBuffer.byteArray();
  }

  public void writeValue(final DbValue value) {
    value.write(valueBuffer, 0);
  }

  public byte[] getValueBufferArray() {
    return valueBuffer.byteArray();
  }

  public void wrapKeyView(final byte[] key) {
    if (key != null) {
      final int columnFamilyKeyLength = columnFamilyKey.getLength();
      final int offset = columnFamilyKeyLength;
      final int length = key.length - columnFamilyKeyLength;

      // wrap without the column family key
      keyViewBuffer.wrap(key, offset, length);
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
      final int columFamilyKeyLength;
      final int prefixLength;
      final int offset;

      columnFamilyKey.write(prefixKeyBuffer, 0);
      columFamilyKeyLength = columnFamilyKey.getLength();
      offset = columFamilyKeyLength;

      key.write(prefixKeyBuffer, offset);
      prefixLength = columFamilyKeyLength + key.getLength();

      prefixKeyConsumer.accept(prefixKeyBuffer.byteArray(), prefixLength);
    } finally {
      prefixKeyBuffers.add(prefixKeyBuffer);
    }
  }

  ByteBuffer keyWithColumnFamily(DbKey key) {
    final var columnFamilyKeyLength = columnFamilyKey.getLength();
    final var bytes = ByteBuffer.allocate(columnFamilyKeyLength + key.getLength());
    final var buffer = new UnsafeBuffer(bytes);

    columnFamilyKey.write(buffer, 0);
    key.write(buffer, columnFamilyKeyLength);
    return bytes;
  }
}
