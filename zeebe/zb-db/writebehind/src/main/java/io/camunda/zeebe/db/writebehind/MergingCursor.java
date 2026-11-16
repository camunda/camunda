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
 * Merges a transaction's own writes with one {@link CommittedSource}. On equal keys the own writes
 * win; a delete hides the key.
 *
 * <p>The own writes are read live, so that writes made while iterating are visible as they are with
 * RocksDB's indexed write batch.
 *
 * <p>Each seek picks the committed source: the {@link IndexSource} if the column family has a
 * loaded key index whose window covers the seek key, which opens no storage iterator, else a {@link
 * ViewSource}. A scan that runs out of a partial window continues with a view, see {@link
 * HandoverSource}. An index source can turn stale in the middle of a scan; the cursor then replaces
 * it with a view that it seeks to the current key, so that no key is returned twice or skipped.
 */
final class MergingCursor<K extends Comparable<K>, V, T> implements AutoCloseable {

  private final NavigableMap<K, Version<V>> own;
  private final WriteBehindStore<K, V, T> store;
  private final KeyRange<K> range;
  private @Nullable CommittedSource<K, V, T> committed;

  private boolean forward = true;
  private @Nullable Entry<K, Version<V>> ownEntry;
  private @Nullable K currentKey;
  // null while the current entry comes from the committed source
  private @Nullable V currentValue;

  /**
   * @param range the range of the scan, which restricts the storage view if the cursor needs one
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

  /**
   * Copies or decodes the current value into {@code target}. Returns false if the entry is gone: a
   * commit deleted it while the cursor read from the key index. The cursor then stays at the key
   * and {@link #advance()} moves on.
   */
  boolean readValue(final T target) {
    final var key = current();
    final var value = currentValue;
    if (value != null) {
      store.backend().copy(value, target);
      return true;
    }
    if (!isCommittedAt(key)) {
      // an earlier failed read moved the source on to another key
      return false;
    }
    if (committed().readValueUnlessStale(target)) {
      return true;
    }
    // the entry is current, so it stays when the view still has it
    return restartFromView(key) && committed().readValueUnlessStale(target);
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
    final var index = store.keyIndex(store.backend().groupOf(key));
    if (index != null && useIndex(index, key)) {
      return;
    }
    committed = openView();
    seekInDirection(key);
  }

  /**
   * Positions an index source at {@code key} if the index may serve this scan. A scan that runs out
   * of a partial window into the keys beyond it continues with a view at the bound.
   */
  private boolean useIndex(final KeyIndex<K> index, final K key) {
    // the sequence before the version, see KeyIndex#isStableAsOf
    final long seq = store.committedSeq();
    final long version = index.version();
    final var bound = index.bound();
    if (!index.isStableAsOf(version, seq) || !index.covers(bound, key)) {
      return false;
    }
    store.ensureUsable();
    store.metrics().indexedScans.increment();
    final var source = new IndexSource<>(store, index, version);
    committed =
        bound != null && index.handsOver(forward)
            ? new HandoverSource<>(source, store, range, bound)
            : source;
    seekInDirection(key);
    if (committed().isStale()) {
      restartFromView(key);
    }
    return true;
  }

  private void seekInDirection(final K key) {
    if (forward) {
      committed().seek(key);
    } else {
      committed().seekForPrev(key);
    }
  }

  /**
   * Replaces the stale index source with a view and seeks it to {@code key}. Returns whether it
   * landed exactly on {@code key}.
   */
  private boolean restartFromView(final K key) {
    committed().close();
    store.metrics().keyIndexSwitches.increment();
    committed = openView();
    seekInDirection(key);
    return isCommittedAt(key);
  }

  private ViewSource<K, V, T> openView() {
    return new ViewSource<>(store.backend(), store.openView(range));
  }

  private boolean isCommittedAt(final K key) {
    return committed().isValid() && committed().key().equals(key);
  }

  private CommittedSource<K, V, T> committed() {
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
      if (committed().isStale() && restartFromView(key)) {
        committed().advance();
      }
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
