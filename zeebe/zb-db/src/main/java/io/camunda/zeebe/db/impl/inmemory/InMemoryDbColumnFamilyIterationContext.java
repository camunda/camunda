/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.db.impl.inmemory;

import io.camunda.zeebe.db.DbKey;
import io.camunda.zeebe.db.impl.ZeebeDbConstants;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.function.Consumer;
import org.agrona.ExpandableArrayBuffer;
import org.agrona.concurrent.UnsafeBuffer;

/**
 * This class allows iterating over a subset of keys in the database. The subset is identified by a
 * common prefix for all keys in that subset. It also implements a recursion guard by checking that
 * iterations can only be nested up to a certain maximum depth.
 */
final class InMemoryDbColumnFamilyIterationContext {

  private final long columnFamilyPrefix;

  private final Queue<ExpandableArrayBuffer> prefixKeyBuffers =
      new ArrayDeque<>(List.of(new ExpandableArrayBuffer(), new ExpandableArrayBuffer()));

  InMemoryDbColumnFamilyIterationContext(final long columnFamilyPrefix) {
    this.columnFamilyPrefix = columnFamilyPrefix;
  }

  void withPrefixKey(final DbKey prefix, final Consumer<Bytes> prefixKeyConsumer) {
    if (prefixKeyBuffers.peek() == null) {
      throw new IllegalStateException(
          "Currently nested prefix iterations of this depth are not supported! This will cause unexpected behavior.");
    }

    final ExpandableArrayBuffer prefixKeyBuffer = prefixKeyBuffers.remove();
    try {
      prefixKeyBuffer.putLong(0, columnFamilyPrefix, ZeebeDbConstants.ZB_DB_BYTE_ORDER);
      prefix.write(prefixKeyBuffer, Long.BYTES);
      final int prefixLength = Long.BYTES + prefix.getLength();
      prefixKeyConsumer.accept(Bytes.fromByteArray(prefixKeyBuffer.byteArray(), prefixLength));
    } finally {
      prefixKeyBuffers.add(prefixKeyBuffer);
    }
  }

  public ByteBuffer keyWithColumnFamily(final DbKey key) {
    final var bytes = ByteBuffer.allocate(Long.BYTES + key.getLength());
    final var buffer = new UnsafeBuffer(bytes);

    buffer.putLong(0, columnFamilyPrefix, ZeebeDbConstants.ZB_DB_BYTE_ORDER);
    key.write(buffer, Long.BYTES);
    return bytes;
  }
}
