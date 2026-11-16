/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import java.util.Map.Entry;
import java.util.NavigableMap;
import org.jspecify.annotations.Nullable;

/**
 * Merges a transaction's own writes with a {@link ViewSource} of the committed state. On equal keys
 * the own writes win; a delete hides the key.
 *
 * <p>The own writes are read live, so that writes made while iterating are visible as they are with
 * RocksDB's indexed write batch. Each seek opens a new view of the committed state.
 */
final class MergingCursor<K extends Comparable<K>, V, T> implements AutoCloseable {

  private final NavigableMap<K, Version<V>> own;
  private final WriteBehindStore<K, V, T> store;
  private final KeyRange<K> range;
  private @Nullable ViewSource<K, V, T> committed;

  private boolean forward = true;
  private @Nullable Entry<K, Version<V>> ownEntry;
  private @Nullable K currentKey;
  // null while the current entry comes from the committed source
  private @Nullable V currentValue;

  /**
   * @param range the range of the scan, which restricts the storage view
   */
  MergingCursor(
      final NavigableMap<K, Version<V>> own,
      final WriteBehindStore<K, V, T> store,
      final KeyRange<K> range) {
    this.own = own;
    this.store = store;
    this.range = range;
  }

  void seek(final K key) {
    forward = true;
    ownEntry = own.ceilingEntry(key);
    seekCommitted(key);
    settle();
  }

  void seekForPrev(final K key) {
    forward = false;
    ownEntry = own.floorEntry(key);
    seekCommitted(key);
    settle();
  }

  boolean isValid() {
    return currentKey != null;
  }

  /** Moves on in the direction of the last seek. */
  void advance() {
    advancePast(current());
    settle();
  }

  /** The current key. Shared: callers must not change it. */
  K key() {
    return current();
  }

  /** Copies or decodes the current value into {@code target}. */
  void readValue(final T target) {
    current();
    final var value = currentValue;
    if (value != null) {
      store.backend().copy(value, target);
    } else {
      committed().readValue(target);
    }
  }

  @Override
  public void close() {
    if (committed != null) {
      committed.close();
      committed = null;
    }
  }

  private void seekCommitted(final K key) {
    close();
    committed = new ViewSource<>(store.backend(), store.openView(range));
    if (forward) {
      committed().seek(key);
    } else {
      committed().seekForPrev(key);
    }
  }

  private boolean isCommittedAt(final K key) {
    return committed().isValid() && committed().key().equals(key);
  }

  private ViewSource<K, V, T> committed() {
    final var source = committed;
    if (source == null) {
      throw new IllegalStateException("Expected the cursor to be positioned, but it is not");
    }
    return source;
  }

  /** Moves to the first entry in iteration order that isn't deleted, or to the end. */
  private void settle() {
    while (true) {
      final @Nullable K key = firstKey();
      if (key == null) {
        currentKey = null;
        currentValue = null;
        return;
      }

      if (ownEntry != null && ownEntry.getKey().equals(key)) {
        final var version = ownEntry.getValue();
        if (!version.isTombstone()) {
          currentKey = key;
          currentValue = version.value();
          return;
        }
      } else {
        currentKey = key;
        currentValue = null;
        return;
      }
      advancePast(key);
    }
  }

  /** The smallest key of both sources in iteration order, or null if both are exhausted. */
  private @Nullable K firstKey() {
    final K ownKey = ownEntry == null ? null : ownEntry.getKey();
    if (!committed().isValid()) {
      return ownKey;
    }
    final K committedKey = committed().key();
    if (ownKey == null) {
      return committedKey;
    }
    final int comparison = ownKey.compareTo(committedKey);
    return (forward ? comparison <= 0 : comparison >= 0) ? ownKey : committedKey;
  }

  /**
   * Moves both sources past {@code key}. The own writes are looked up again, as they may change.
   */
  private void advancePast(final K key) {
    ownEntry = forward ? own.higherEntry(key) : own.lowerEntry(key);
    if (isCommittedAt(key)) {
      committed().advance();
    }
  }

  private K current() {
    final var key = currentKey;
    if (key == null) {
      throw new IllegalStateException("Expected the cursor to be valid, but it is not");
    }
    return key;
  }
}
