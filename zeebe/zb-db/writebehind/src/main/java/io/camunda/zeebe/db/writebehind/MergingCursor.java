/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import io.camunda.zeebe.db.DbIterator;
import io.camunda.zeebe.db.DbValue;
import java.util.Arrays;
import java.util.Map.Entry;
import java.util.NavigableMap;
import org.agrona.concurrent.UnsafeBuffer;
import org.jspecify.annotations.Nullable;

/**
 * Merges three sorted sources into one view, in priority order: the transaction's own writes, the
 * unflushed committed versions, and the storage. On equal keys the first source wins; a delete
 * hides the key.
 *
 * <p>The own writes are read live, so that writes made while iterating are visible as they are with
 * RocksDB's indexed write batch. The other two sources are fixed when the cursor is created.
 *
 * <p>Values from memory are copied out of their objects; only values from the storage are decoded,
 * and only when {@link #readValue} asks for them.
 */
final class MergingCursor implements AutoCloseable {

  private final NavigableMap<RawKey, Version> own;
  private final NavigableMap<RawKey, Version> dirty;
  private final DbIterator stored;

  private boolean forward = true;
  private @Nullable Entry<RawKey, Version> ownEntry;
  private @Nullable Entry<RawKey, Version> dirtyEntry;
  private byte @Nullable [] storedKey;
  private byte @Nullable [] currentKey;
  // null while the current entry comes from the storage
  private @Nullable DbValue currentValue;

  MergingCursor(final NavigableMap<RawKey, Version> own, final WriteBehindStore.View view) {
    this.own = own;
    dirty = view.dirty();
    stored = view.storageIterator();
  }

  void seek(final byte[] key) {
    forward = true;
    final var target = RawKey.copyOf(key);
    ownEntry = own.ceilingEntry(target);
    dirtyEntry = dirty.ceilingEntry(target);
    stored.seek(key);
    storedKey = stored.isValid() ? stored.key() : null;
    settle();
  }

  void seekForPrev(final byte[] key) {
    forward = false;
    final var target = RawKey.copyOf(key);
    ownEntry = own.floorEntry(target);
    dirtyEntry = dirty.floorEntry(target);
    stored.seekForPrev(key);
    storedKey = stored.isValid() ? stored.key() : null;
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

  /** Copies or decodes the current value into {@code target}. */
  void readValue(final DbValue target) {
    current();
    final var value = currentValue;
    if (value != null) {
      Values.copy(value, target);
    } else {
      final var bytes = stored.value();
      target.wrap(new UnsafeBuffer(bytes), 0, bytes.length);
    }
  }

  @Override
  public void close() {
    stored.close();
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

      final @Nullable Version version;
      if (ownEntry != null && Arrays.equals(ownEntry.getKey().bytes(), key)) {
        version = ownEntry.getValue();
      } else if (dirtyEntry != null && Arrays.equals(dirtyEntry.getKey().bytes(), key)) {
        version = dirtyEntry.getValue();
      } else {
        version = null;
      }

      if (version == null || !version.isTombstone()) {
        currentKey = key;
        currentValue = version == null ? null : version.value();
        return;
      }
      advancePast(key);
    }
  }

  /** The smallest key across all sources in iteration order, or null if all are exhausted. */
  private byte @Nullable [] firstKey() {
    byte @Nullable [] first = null;
    if (ownEntry != null) {
      first = ownEntry.getKey().bytes();
    }
    if (dirtyEntry != null && (first == null || before(dirtyEntry.getKey().bytes(), first))) {
      first = dirtyEntry.getKey().bytes();
    }
    if (storedKey != null && (first == null || before(storedKey, first))) {
      first = storedKey;
    }
    return first;
  }

  /**
   * Moves every source past {@code key}. The own writes are looked up again, as they may change.
   */
  private void advancePast(final byte[] key) {
    final var rawKey = RawKey.copyOf(key);
    ownEntry = forward ? own.higherEntry(rawKey) : own.lowerEntry(rawKey);
    if (dirtyEntry != null && Arrays.equals(dirtyEntry.getKey().bytes(), key)) {
      dirtyEntry = forward ? dirty.higherEntry(rawKey) : dirty.lowerEntry(rawKey);
    }
    if (storedKey != null && Arrays.equals(storedKey, key)) {
      if (forward) {
        stored.next();
      } else {
        stored.prev();
      }
      storedKey = stored.isValid() ? stored.key() : null;
    }
  }

  private boolean before(final byte[] a, final byte[] b) {
    final int comparison = Arrays.compareUnsigned(a, b);
    return forward ? comparison < 0 : comparison > 0;
  }

  private byte[] current() {
    final var key = currentKey;
    if (key == null) {
      throw new IllegalStateException("Expected the cursor to be valid, but it is not");
    }
    return key;
  }
}
