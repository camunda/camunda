/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.cache;

import io.camunda.zeebe.protocol.EnumValue;
import java.util.Arrays;

/**
 * Identifies a cached entry: a column family and the key as written by {@link
 * io.camunda.zeebe.db.DbKey#write}.
 *
 * <p>Keys sort by column family, then by the unsigned byte order of the key, which is the order the
 * storage iterates in. The key array is shared with the cache and the DirtyIndex, so it must never
 * be mutated or handed out.
 */
final class CacheKey implements Comparable<CacheKey> {

  private final EnumValue columnFamily;
  private final byte[] key;

  CacheKey(final EnumValue columnFamily, final byte[] key) {
    this.columnFamily = columnFamily;
    this.key = key;
  }

  EnumValue columnFamily() {
    return columnFamily;
  }

  byte[] key() {
    return key;
  }

  @Override
  public int compareTo(final CacheKey other) {
    final int byColumnFamily =
        Integer.compare(columnFamily.getValue(), other.columnFamily.getValue());
    return byColumnFamily != 0 ? byColumnFamily : Arrays.compareUnsigned(key, other.key);
  }

  @Override
  public boolean equals(final Object o) {
    return o instanceof final CacheKey other
        && columnFamily.getValue() == other.columnFamily.getValue()
        && Arrays.equals(key, other.key);
  }

  @Override
  public int hashCode() {
    return 31 * columnFamily.getValue() + Arrays.hashCode(key);
  }

  @Override
  public String toString() {
    return "CacheKey{columnFamily=" + columnFamily + ", key=" + Arrays.toString(key) + '}';
  }
}
