/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import java.util.Arrays;
import org.jspecify.annotations.Nullable;

/**
 * Immutable raw key: column family prefix plus key. Sorts bytewise (unsigned), like the storage and
 * RocksDB's default comparator, so the in-memory maps iterate in the same order as the storage.
 */
final class RawKey implements Comparable<RawKey> {

  private final byte[] bytes;
  private final int hash;

  private RawKey(final byte[] bytes) {
    this.bytes = bytes;
    hash = Arrays.hashCode(bytes);
  }

  /** Copies the first {@code length} bytes, so later changes to {@code source} don't leak in. */
  static RawKey copyOf(final byte[] source, final int length) {
    return new RawKey(Arrays.copyOf(source, length));
  }

  /** Takes ownership of {@code bytes}: nobody may change them afterwards. */
  static RawKey of(final byte[] bytes) {
    return new RawKey(bytes);
  }

  static RawKey copyOf(final byte[] source) {
    return copyOf(source, source.length);
  }

  /**
   * Returns the first key after every key that starts with the given prefix, or null if there is
   * none (the prefix is empty or all 0xFF).
   */
  static @Nullable RawKey prefixEnd(final byte[] prefix, final int prefixLength) {
    for (int i = prefixLength - 1; i >= 0; i--) {
      if (prefix[i] != (byte) 0xFF) {
        final var end = Arrays.copyOf(prefix, i + 1);
        end[i]++;
        return new RawKey(end);
      }
    }
    return null;
  }

  /** The key bytes. Shared: callers must not change them, and must copy before handing them out. */
  byte[] bytes() {
    return bytes;
  }

  @Override
  public int compareTo(final RawKey other) {
    return Arrays.compareUnsigned(bytes, other.bytes);
  }

  @Override
  public boolean equals(final @Nullable Object other) {
    return this == other || (other instanceof final RawKey key && Arrays.equals(bytes, key.bytes));
  }

  @Override
  public int hashCode() {
    return hash;
  }

  @Override
  public String toString() {
    return Arrays.toString(bytes);
  }
}
