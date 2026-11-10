/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import io.camunda.zeebe.db.DbIterator;
import java.util.Arrays;
import java.util.Map.Entry;
import java.util.NavigableMap;
import org.jspecify.annotations.Nullable;

/**
 * Merges three sorted sources into one view, in priority order: the transaction's own writes, the
 * unflushed committed versions, and the storage. On equal keys the first source wins; a delete
 * hides the key.
 *
 * <p>The own writes are read live, so that writes made while iterating are visible as they are with
 * RocksDB's indexed write batch. The other two sources are fixed when the iterator is created.
 */
final class MergingDbIterator implements DbIterator {

  private final NavigableMap<RawKey, byte[]> own;
  private final NavigableMap<RawKey, Version> dirty;
  private final DbIterator stored;

  private boolean forward = true;
  private @Nullable Entry<RawKey, byte[]> ownEntry;
  private @Nullable Entry<RawKey, Version> dirtyEntry;
  private byte @Nullable [] storedKey;
  private byte @Nullable [] currentKey;
  private byte @Nullable [] currentValue;

  MergingDbIterator(final NavigableMap<RawKey, byte[]> own, final WriteBehindStore.View view) {
    this.own = own;
    dirty = view.dirty();
    stored = view.storageIterator();
  }

  @Override
  public void seek(final byte[] key) {
    forward = true;
    final var target = RawKey.copyOf(key);
    ownEntry = own.ceilingEntry(target);
    dirtyEntry = dirty.ceilingEntry(target);
    stored.seek(key);
    storedKey = stored.isValid() ? stored.key() : null;
    settle();
  }

  @Override
  public void seekForPrev(final byte[] key) {
    forward = false;
    final var target = RawKey.copyOf(key);
    ownEntry = own.floorEntry(target);
    dirtyEntry = dirty.floorEntry(target);
    stored.seekForPrev(key);
    storedKey = stored.isValid() ? stored.key() : null;
    settle();
  }

  @Override
  public boolean isValid() {
    return currentKey != null;
  }

  @Override
  public void next() {
    if (!forward) {
      throw new IllegalStateException("Expected to iterate in one direction, but changed to next");
    }
    advancePast(current());
    settle();
  }

  @Override
  public void prev() {
    if (forward) {
      throw new IllegalStateException("Expected to iterate in one direction, but changed to prev");
    }
    advancePast(current());
    settle();
  }

  @Override
  public byte[] key() {
    return current().clone();
  }

  @Override
  public byte[] value() {
    return currentValue().clone();
  }

  @Override
  public int key(final byte[] buffer) {
    return copyInto(current(), buffer);
  }

  @Override
  public int value(final byte[] buffer) {
    return copyInto(currentValue(), buffer);
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

      final byte @Nullable [] value;
      if (ownEntry != null && Arrays.equals(ownEntry.getKey().bytes(), key)) {
        final var ownValue = ownEntry.getValue();
        value = ownValue == WriteBehindTransaction.DELETED ? null : ownValue;
      } else if (dirtyEntry != null && Arrays.equals(dirtyEntry.getKey().bytes(), key)) {
        value = dirtyEntry.getValue().bytes();
      } else {
        value = stored.value();
      }

      if (value != null) {
        currentKey = key;
        currentValue = value;
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
      throw new IllegalStateException("Expected the iterator to be valid, but it is not");
    }
    return key;
  }

  private byte[] currentValue() {
    final var value = currentValue;
    if (value == null) {
      throw new IllegalStateException("Expected the iterator to be valid, but it is not");
    }
    return value;
  }

  private static int copyInto(final byte[] source, final byte[] buffer) {
    System.arraycopy(source, 0, buffer, 0, Math.min(source.length, buffer.length));
    return source.length;
  }
}
