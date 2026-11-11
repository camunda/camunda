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
 * The committed state at one instant: a copy of the unflushed versions merged with a storage
 * iterator. On equal keys the unflushed version wins; a delete hides the key.
 *
 * <p>Values from memory are copied out of their objects; only values from the storage are decoded,
 * and only when {@link #readValueUnlessStale} asks for them.
 */
final class ViewSource implements CommittedSource {

  private final NavigableMap<RawKey, Version> dirty;
  private final DbIterator stored;

  private boolean forward = true;
  private @Nullable Entry<RawKey, Version> dirtyEntry;
  private byte @Nullable [] storedKey;
  private byte @Nullable [] currentKey;
  // null while the current entry comes from the storage
  private @Nullable DbValue currentValue;

  ViewSource(final WriteBehindStore.View view) {
    dirty = view.dirty();
    stored = view.storageIterator();
  }

  @Override
  public void seek(final byte[] key) {
    forward = true;
    dirtyEntry = dirty.ceilingEntry(RawKey.copyOf(key));
    stored.seek(key);
    storedKey = stored.isValid() ? stored.key() : null;
    settle();
  }

  @Override
  public void seekForPrev(final byte[] key) {
    forward = false;
    dirtyEntry = dirty.floorEntry(RawKey.copyOf(key));
    stored.seekForPrev(key);
    storedKey = stored.isValid() ? stored.key() : null;
    settle();
  }

  @Override
  public boolean isValid() {
    return currentKey != null;
  }

  @Override
  public byte[] key() {
    return current();
  }

  @Override
  public void advance() {
    advancePast(current());
    settle();
  }

  @Override
  public boolean readValueUnlessStale(final DbValue target) {
    current();
    final var value = currentValue;
    if (value != null) {
      Values.copy(value, target);
    } else {
      final var bytes = stored.value();
      target.wrap(new UnsafeBuffer(bytes), 0, bytes.length);
    }
    return true;
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

      final @Nullable Version version =
          dirtyEntry != null && Arrays.equals(dirtyEntry.getKey().bytes(), key)
              ? dirtyEntry.getValue()
              : null;
      if (version == null || !version.isTombstone()) {
        currentKey = key;
        currentValue = version == null ? null : version.value();
        return;
      }
      advancePast(key);
    }
  }

  private byte @Nullable [] firstKey() {
    final byte[] dirtyKey = dirtyEntry == null ? null : dirtyEntry.getKey().bytes();
    if (dirtyKey == null) {
      return storedKey;
    }
    if (storedKey == null) {
      return dirtyKey;
    }
    final int comparison = Arrays.compareUnsigned(dirtyKey, storedKey);
    return (forward ? comparison <= 0 : comparison >= 0) ? dirtyKey : storedKey;
  }

  private void advancePast(final byte[] key) {
    if (dirtyEntry != null && Arrays.equals(dirtyEntry.getKey().bytes(), key)) {
      final var rawKey = RawKey.copyOf(key);
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

  private byte[] current() {
    final var key = currentKey;
    if (key == null) {
      throw new IllegalStateException("Expected the source to be valid, but it is not");
    }
    return key;
  }
}
