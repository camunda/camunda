/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.rdbms;

import java.util.Arrays;
import org.jspecify.annotations.Nullable;

/** Turns RocksDB's prefix and start key into a key range the database can search. */
final class KeyRanges {

  private KeyRanges() {}

  /**
   * Returns the first key after all keys that start with {@code prefix}, or {@code null} if no key
   * comes after them: when the prefix is empty or only has {@code 0xFF} bytes.
   */
  static byte @Nullable [] next(final byte[] prefix) {
    for (int i = prefix.length - 1; i >= 0; i--) {
      if (prefix[i] != (byte) 0xFF) {
        final var next = Arrays.copyOf(prefix, i + 1);
        next[i]++;
        return next;
      }
    }
    return null;
  }

  static boolean startsWith(final byte[] key, final byte[] prefix) {
    return key.length >= prefix.length
        && Arrays.equals(key, 0, prefix.length, prefix, 0, prefix.length);
  }

  /**
   * The keys a forward scan reads: from {@code start} to the end of the prefix. A start outside the
   * prefix needs no special case, because the scan stops at the first key without the prefix, as
   * RocksDB does.
   */
  static StateRange forward(
      final int partitionId, final int columnFamily, final byte[] prefix, final byte[] start) {
    return new StateRange(partitionId, columnFamily, emptyToNull(start), next(prefix), false);
  }

  /** The keys a reverse scan reads: from {@code start} down to the start of the prefix. */
  static StateRange reverse(
      final int partitionId, final int columnFamily, final byte[] prefix, final byte[] start) {
    // the range's end is exclusive, and start + 0x00 is the first key after start
    final var to = Arrays.copyOf(start, start.length + 1);
    return new StateRange(partitionId, columnFamily, emptyToNull(prefix), to, true);
  }

  /** All keys that start with {@code prefix}. */
  static StateRange withPrefix(final int partitionId, final int columnFamily, final byte[] prefix) {
    return new StateRange(partitionId, columnFamily, emptyToNull(prefix), next(prefix), false);
  }

  private static byte @Nullable [] emptyToNull(final byte @Nullable [] bound) {
    return bound == null || bound.length == 0 ? null : bound;
  }
}
