/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import io.camunda.zeebe.db.DbValue;
import java.util.Arrays;
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
final class MergingCursor implements AutoCloseable {

  private final NavigableMap<RawKey, Version> own;
  private final WriteBehindStore store;
  private final byte[] prefix;
  private @Nullable CommittedSource committed;

  private boolean forward = true;
  private @Nullable Entry<RawKey, Version> ownEntry;
  private byte @Nullable [] currentKey;
  // null while the current entry comes from the committed source
  private @Nullable DbValue currentValue;

  /**
   * @param prefix the prefix of the scan, which restricts the storage view if the cursor needs one
   */
  MergingCursor(
      final NavigableMap<RawKey, Version> own, final WriteBehindStore store, final byte[] prefix) {
    this.own = own;
    this.store = store;
    this.prefix = prefix;
  }

  void seek(final byte[] key) {
    forward = true;
    ownEntry = own.ceilingEntry(RawKey.copyOf(key));
    seekCommitted(key);
    settle();
  }

  void seekForPrev(final byte[] key) {
    forward = false;
    ownEntry = own.floorEntry(RawKey.copyOf(key));
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

  /** The current raw key. Shared: callers must not change it. */
  byte[] key() {
    return current();
  }

  /**
   * Copies or decodes the current value into {@code target}. Returns false if the entry is gone: a
   * commit deleted it while the cursor read from the key index. The cursor then stays at the key
   * and {@link #advance()} moves on.
   */
  boolean readValue(final DbValue target) {
    final var key = current();
    final var value = currentValue;
    if (value != null) {
      Values.copy(value, target);
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

  private void seekCommitted(final byte[] key) {
    close();
    final var index = store.keyIndex(RawKey.columnFamilyOf(key));
    final long version = index == null ? 1 : index.version();
    final var bound = index == null ? null : index.bound();
    if (index != null && index.isStable(version) && index.covers(bound, key)) {
      store.ensureUsable();
      store.metrics().indexedScans.increment();
      final var source = new IndexSource(store, index, version);
      committed =
          bound != null && index.handsOver(forward)
              ? new HandoverSource(
                  source,
                  this::openView,
                  bound.bytes(),
                  store.metrics().keyIndexHandovers::increment)
              : source;
    } else {
      committed = openView();
    }
    seekInDirection(key);
    if (committed().isStale()) {
      restartFromView(key);
    }
  }

  private void seekInDirection(final byte[] key) {
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
  private boolean restartFromView(final byte[] key) {
    committed().close();
    store.metrics().keyIndexSwitches.increment();
    committed = openView();
    seekInDirection(key);
    return isCommittedAt(key);
  }

  private ViewSource openView() {
    return new ViewSource(store.openView(prefix, prefix.length));
  }

  private boolean isCommittedAt(final byte[] key) {
    return committed().isValid() && Arrays.equals(committed().key(), key);
  }

  private CommittedSource committed() {
    final var source = committed;
    if (source == null) {
      throw new IllegalStateException("Expected the cursor to be positioned, but it is not");
    }
    return source;
  }

  /** Moves to the first entry in iteration order that isn't deleted, or to the end. */
  private void settle() {
    while (true) {
      final byte @Nullable [] key = firstKey();
      if (key == null) {
        currentKey = null;
        currentValue = null;
        return;
      }

      if (ownEntry != null && Arrays.equals(ownEntry.getKey().bytes(), key)) {
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
  private byte @Nullable [] firstKey() {
    final byte[] ownKey = ownEntry == null ? null : ownEntry.getKey().bytes();
    if (!committed().isValid()) {
      return ownKey;
    }
    final byte[] committedKey = committed().key();
    if (ownKey == null) {
      return committedKey;
    }
    final int comparison = Arrays.compareUnsigned(ownKey, committedKey);
    return (forward ? comparison <= 0 : comparison >= 0) ? ownKey : committedKey;
  }

  /**
   * Moves both sources past {@code key}. The own writes are looked up again, as they may change.
   */
  private void advancePast(final byte[] key) {
    final var rawKey = RawKey.copyOf(key);
    ownEntry = forward ? own.higherEntry(rawKey) : own.lowerEntry(rawKey);
    if (isCommittedAt(key)) {
      committed().advance();
      if (committed().isStale() && restartFromView(key)) {
        committed().advance();
      }
    }
  }

  private byte[] current() {
    final var key = currentKey;
    if (key == null) {
      throw new IllegalStateException("Expected the cursor to be valid, but it is not");
    }
    return key;
  }
}
