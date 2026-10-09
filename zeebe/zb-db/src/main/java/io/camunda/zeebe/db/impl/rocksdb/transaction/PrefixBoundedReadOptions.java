/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.impl.rocksdb.transaction;

import io.camunda.zeebe.db.impl.rocksdb.PrefixReadOptions;
import java.nio.ByteBuffer;
import org.rocksdb.DirectSlice;
import org.rocksdb.ReadOptions;

/**
 * Prefix read options bounded to a given key prefix, so RocksDB stops iterating at the end of the
 * prefix instead of at the end of the column family prefix extracted by the prefix extractor.
 * Forward iterations get {@code iterate_upper_bound} set to the first key after the prefix, reverse
 * iterations additionally get {@code iterate_lower_bound} set to the prefix itself.
 *
 * <p>Without the bound, a seek into a prefix with no live keys has to step over every tombstone
 * until the next live key of the whole column family, which can be millions of entries for
 * queue-like column families with high churn (e.g. activatable jobs). With it, RocksDB stops at the
 * bound, and the cost is limited to tombstones within the prefix.
 *
 * <p>The bounds are backed by reusable direct buffers, so bounding an iteration does not allocate.
 * Both bounds are set on every call (the lower bound to the empty key for forward iterations), so a
 * bound left over from a previous iteration can never hide keys of the current one. Forward
 * iterations deliberately get no lower bound: RocksDB would move a seek target below the bound up
 * to it, while today such a seek lands outside the prefix and ends the iteration.
 *
 * <p>An instance must not be shared between iterators that are open at the same time, since the
 * iterator reads the bound throughout its lifetime. Not thread safe.
 */
final class PrefixBoundedReadOptions implements AutoCloseable {

  private static final int INITIAL_CAPACITY = 128;

  private final ReadOptions readOptions = PrefixReadOptions.readOptions();
  private ByteBuffer lowerBound = ByteBuffer.allocateDirect(INITIAL_CAPACITY);
  private DirectSlice lowerBoundSlice = new DirectSlice(lowerBound, 0);
  private ByteBuffer upperBound = ByteBuffer.allocateDirect(INITIAL_CAPACITY);
  private DirectSlice upperBoundSlice = new DirectSlice(upperBound, 0);

  PrefixBoundedReadOptions() {
    readOptions.setIterateLowerBound(lowerBoundSlice);
    readOptions.setIterateUpperBound(upperBoundSlice);
  }

  /**
   * Bounds a forward iteration to keys starting with the given prefix, and returns the read options
   * to create the iterator with. The returned options stay valid until the next call on this
   * instance.
   */
  ReadOptions forward(final byte[] prefix, final int prefixLength) {
    lowerBoundSlice.setLength(0);
    setUpperBound(prefix, prefixLength);
    return readOptions;
  }

  /**
   * Bounds a reverse iteration to keys starting with the given prefix, and returns the read options
   * to create the iterator with. The returned options stay valid until the next call on this
   * instance.
   */
  ReadOptions reverse(final byte[] prefix, final int prefixLength) {
    ensureLowerBoundCapacity(prefixLength);
    lowerBound.put(0, prefix, 0, prefixLength);
    lowerBoundSlice.setLength(prefixLength);
    setUpperBound(prefix, prefixLength);
    return readOptions;
  }

  private void setUpperBound(final byte[] prefix, final int prefixLength) {
    final int boundLength = successorLength(prefix, prefixLength);
    ensureUpperBoundCapacity(boundLength);
    upperBound.put(0, prefix, 0, boundLength);
    upperBound.put(boundLength - 1, (byte) (prefix[boundLength - 1] + 1));
    upperBoundSlice.setLength(boundLength);
  }

  @Override
  public void close() {
    readOptions.close();
    lowerBoundSlice.close();
    upperBoundSlice.close();
  }

  /**
   * The successor of a prefix is the prefix with trailing 0xFF bytes removed and the last remaining
   * byte incremented. Our keys always start with the column family id as a big-endian long, whose
   * most significant byte is 0, so a successor always exists.
   */
  private static int successorLength(final byte[] prefix, final int prefixLength) {
    for (int i = prefixLength - 1; i >= 0; i--) {
      if (prefix[i] != (byte) 0xFF) {
        return i + 1;
      }
    }
    throw new IllegalArgumentException("Expected prefix to have a successor, but it is all 0xFF");
  }

  private void ensureLowerBoundCapacity(final int length) {
    if (lowerBound.capacity() >= length) {
      return;
    }
    final var newBound = ByteBuffer.allocateDirect(grow(lowerBound, length));
    final var newSlice = new DirectSlice(newBound, 0);
    readOptions.setIterateLowerBound(newSlice);
    lowerBoundSlice.close();
    lowerBound = newBound;
    lowerBoundSlice = newSlice;
  }

  private void ensureUpperBoundCapacity(final int length) {
    if (upperBound.capacity() >= length) {
      return;
    }
    final var newBound = ByteBuffer.allocateDirect(grow(upperBound, length));
    final var newSlice = new DirectSlice(newBound, 0);
    readOptions.setIterateUpperBound(newSlice);
    upperBoundSlice.close();
    upperBound = newBound;
    upperBoundSlice = newSlice;
  }

  private static int grow(final ByteBuffer buffer, final int minCapacity) {
    return Math.max(minCapacity, buffer.capacity() << 1);
  }
}
