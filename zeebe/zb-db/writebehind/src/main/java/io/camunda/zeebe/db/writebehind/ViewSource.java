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
 * The committed state at one instant: a copy of the unflushed versions merged with a storage
 * iterator. On equal keys the unflushed version wins; a delete hides the key.
 *
 * <p>Values from memory are copied out of their objects; only values from the storage are decoded,
 * and only when {@link #readValueUnlessStale} asks for them.
 */
final class ViewSource<K extends Comparable<K>, V> implements CommittedSource<K, V> {

  private final Backend<K, V> backend;
  private final NavigableMap<K, Version<V>> dirty;
  private final Backend.Cursor<K, V> stored;

  private boolean forward = true;
  private @Nullable Entry<K, Version<V>> dirtyEntry;
  private @Nullable K storedKey;
  private @Nullable K currentKey;
  // null while the current entry comes from the storage
  private @Nullable V currentValue;

  ViewSource(final Backend<K, V> backend, final WriteBehindStore.View<K, V> view) {
    this.backend = backend;
    dirty = view.dirty();
    stored = view.storageCursor();
  }

  @Override
  public void seek(final K key) {
    forward = true;
    dirtyEntry = dirty.ceilingEntry(key);
    stored.seek(key);
    storedKey = stored.isValid() ? stored.key() : null;
    settle();
  }

  @Override
  public void seekForPrev(final K key) {
    forward = false;
    dirtyEntry = dirty.floorEntry(key);
    stored.seekForPrev(key);
    storedKey = stored.isValid() ? stored.key() : null;
    settle();
  }

  @Override
  public boolean isValid() {
    return currentKey != null;
  }

  @Override
  public K key() {
    return current();
  }

  @Override
  public void advance() {
    advancePast(current());
    settle();
  }

  @Override
  public boolean readValueUnlessStale(final V target) {
    current();
    final var value = currentValue;
    if (value != null) {
      backend.copy(value, target);
    } else {
      stored.readValue(target);
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
      final @Nullable K key = firstKey();
      if (key == null) {
        currentKey = null;
        currentValue = null;
        return;
      }

      final @Nullable Version<V> version =
          dirtyEntry != null && dirtyEntry.getKey().equals(key) ? dirtyEntry.getValue() : null;
      if (version == null || !version.isTombstone()) {
        currentKey = key;
        currentValue = version == null ? null : version.value();
        return;
      }
      advancePast(key);
    }
  }

  private @Nullable K firstKey() {
    final K dirtyKey = dirtyEntry == null ? null : dirtyEntry.getKey();
    if (dirtyKey == null) {
      return storedKey;
    }
    if (storedKey == null) {
      return dirtyKey;
    }
    final int comparison = dirtyKey.compareTo(storedKey);
    return (forward ? comparison <= 0 : comparison >= 0) ? dirtyKey : storedKey;
  }

  private void advancePast(final K key) {
    if (dirtyEntry != null && dirtyEntry.getKey().equals(key)) {
      dirtyEntry = forward ? dirty.higherEntry(key) : dirty.lowerEntry(key);
    }
    if (storedKey != null && storedKey.equals(key)) {
      if (forward) {
        stored.next();
      } else {
        stored.prev();
      }
      storedKey = stored.isValid() ? stored.key() : null;
    }
  }

  private K current() {
    final var key = currentKey;
    if (key == null) {
      throw new IllegalStateException("Expected the source to be valid, but it is not");
    }
    return key;
  }
}
