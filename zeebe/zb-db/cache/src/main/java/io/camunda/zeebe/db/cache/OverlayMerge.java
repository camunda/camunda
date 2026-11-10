/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.cache;

import static java.util.Objects.requireNonNull;

import io.camunda.zeebe.db.cache.PassThroughColumnFamilies.ScanVisitor;
import io.camunda.zeebe.protocol.EnumValue;
import java.util.Arrays;
import java.util.NavigableMap;
import org.jspecify.annotations.Nullable;

/**
 * Merges a backend scan with the versions the backend doesn't have: the transaction's own writes,
 * read live so that writes made during the scan count, and a copy of the unflushed versions taken
 * when the scan began. On equal keys, the transaction wins over the copy, which wins over the
 * backend. A tombstone hides its key.
 *
 * <p>The backend pushes its entries through {@link #onBackendEntry}; before each one, the merge
 * emits the overlay entries that come first. {@link #finish} emits the rest. Like RocksDB, the
 * merge stops at the first visible key outside the prefix.
 */
final class OverlayMerge {

  private final EnumValue columnFamily;
  private final byte[] prefix;
  private final boolean reverse;
  private final boolean keysOnly;
  private final CachedTransaction transaction;
  private final NavigableMap<CacheKey, CachedValue> unflushed;
  private final ScanVisitor visitor;

  private CacheKey cursor;
  private boolean cursorInclusive = true;
  private boolean stopped;

  /**
   * @param startAt the first key of the scan, inclusive
   * @param unflushed the unflushed versions of the scanned range, copied when the scan began
   * @param visitor receives the merged entries; values are {@code null} in a key-only scan
   */
  OverlayMerge(
      final EnumValue columnFamily,
      final byte[] prefix,
      final byte[] startAt,
      final boolean reverse,
      final boolean keysOnly,
      final CachedTransaction transaction,
      final NavigableMap<CacheKey, CachedValue> unflushed,
      final ScanVisitor visitor) {
    this.columnFamily = columnFamily;
    this.prefix = prefix;
    this.reverse = reverse;
    this.keysOnly = keysOnly;
    this.transaction = transaction;
    this.unflushed = unflushed;
    this.visitor = visitor;
    cursor = new CacheKey(columnFamily, startAt);
  }

  /** Merges in the backend's next entry. Returns whether the backend scan should go on. */
  boolean onBackendEntry(final byte[] key, final byte @Nullable [] value) {
    final var cacheKey = new CacheKey(columnFamily, key);
    emitOverlayBefore(cacheKey);
    if (stopped) {
      return false;
    }

    final var own = transaction.get(cacheKey);
    if (own != null) {
      emitCopyIfVisible(cacheKey, own.bytes());
    } else {
      final var version = unflushed.get(cacheKey);
      if (version != null) {
        emitCopyIfVisible(cacheKey, version.bytes());
      } else {
        emit(key, value);
      }
    }
    moveCursorPast(cacheKey);
    return !stopped;
  }

  /** Emits the overlay entries after the backend's last one. */
  void finish() {
    emitOverlayBefore(null);
  }

  static boolean startsWith(final byte[] key, final byte[] prefix) {
    return key.length >= prefix.length
        && Arrays.equals(key, 0, prefix.length, prefix, 0, prefix.length);
  }

  private void emitOverlayBefore(final @Nullable CacheKey bound) {
    while (!stopped) {
      final var next = nextOverlayKey();
      if (next == null || (bound != null && !isBefore(next, bound))) {
        return;
      }

      final var own = transaction.get(next);
      final var bytes = own != null ? own.bytes() : requireNonNull(unflushed.get(next)).bytes();
      emitCopyIfVisible(next, bytes);
      moveCursorPast(next);
    }
  }

  private @Nullable CacheKey nextOverlayKey() {
    final var own = transaction.nextKey(cursor, cursorInclusive, reverse);
    final CacheKey version;
    if (reverse) {
      version = cursorInclusive ? unflushed.floorKey(cursor) : unflushed.lowerKey(cursor);
    } else {
      version = cursorInclusive ? unflushed.ceilingKey(cursor) : unflushed.higherKey(cursor);
    }

    if (own == null) {
      return version;
    }
    if (version == null) {
      return own;
    }
    return isBefore(own, version) ? own : version;
  }

  /** Emits a copy of a shared overlay version, unless it's a tombstone. */
  private void emitCopyIfVisible(final CacheKey key, final byte @Nullable [] bytes) {
    if (bytes != null) {
      emit(key.key().clone(), keysOnly ? null : bytes.clone());
    }
  }

  private void emit(final byte[] key, final byte @Nullable [] value) {
    if (!startsWith(key, prefix) || !visitor.visit(key, value)) {
      stopped = true;
    }
  }

  private void moveCursorPast(final CacheKey key) {
    cursor = key;
    cursorInclusive = false;
  }

  private boolean isBefore(final CacheKey key, final CacheKey other) {
    final int comparison = key.compareTo(other);
    return reverse ? comparison > 0 : comparison < 0;
  }
}
