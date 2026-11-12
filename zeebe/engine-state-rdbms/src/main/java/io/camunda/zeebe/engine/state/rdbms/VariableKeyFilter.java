/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.rdbms;

import io.camunda.zeebe.engine.state.rdbms.VariableMapper.VariableKey;
import java.util.concurrent.atomic.AtomicLongArray;
import org.jspecify.annotations.Nullable;

/**
 * Tells whether a variable was certainly never stored, without asking the database: a variable of a
 * scope created after the broker started can only exist if the filter has seen it written.
 *
 * <p>Engine keys only grow, so a scope above the highest scope in the database at the start is new.
 * The variables written since then are remembered in a Bloom filter, which answers "maybe" for a
 * few keys that were never written, and the caller then reads the database as it would without the
 * filter. Its false positive rate grows with the variables written since the start: about 20% after
 * 10 million.
 */
final class VariableKeyFilter {

  private static final int BITS = 1 << 25;
  private static final int HASHES = 3;

  private final long highestScopeAtStart;
  // allocated by the first write, which only the flusher does; 4 MB for a partition that has none
  private volatile @Nullable AtomicLongArray bits;

  VariableKeyFilter(final long highestScopeAtStart) {
    this.highestScopeAtStart = highestScopeAtStart;
  }

  /** Remembers a variable that is written; call it before the row can reach the database. */
  void written(final VariableKey key) {
    if (key.scopeKey() <= highestScopeAtStart) {
      return;
    }
    var filter = bits;
    if (filter == null) {
      filter = new AtomicLongArray(BITS / Long.SIZE);
      bits = filter;
    }
    final long hash = hash(key);
    final long step = hash >>> 32 | 1;
    long probe = hash;
    for (int i = 0; i < HASHES; i++, probe += step) {
      final int bit = (int) (probe & (BITS - 1));
      final long mask = 1L << bit;
      filter.accumulateAndGet(bit >>> 6, mask, (current, added) -> current | added);
    }
  }

  /** Whether the variable was never written: false means that it may exist. */
  boolean neverWritten(final VariableKey key) {
    if (key.scopeKey() <= highestScopeAtStart) {
      return false;
    }
    final var filter = bits;
    if (filter == null) {
      return true;
    }
    final long hash = hash(key);
    final long step = hash >>> 32 | 1;
    long probe = hash;
    for (int i = 0; i < HASHES; i++, probe += step) {
      final int bit = (int) (probe & (BITS - 1));
      if ((filter.get(bit >>> 6) & (1L << bit)) == 0) {
        return true;
      }
    }
    return false;
  }

  private static long hash(final VariableKey key) {
    long h = key.scopeKey() * 0x9E3779B97F4A7C15L + key.name().hashCode();
    h ^= h >>> 32;
    h *= 0xD6E8FEB86659FD93L;
    h ^= h >>> 32;
    return h;
  }
}
