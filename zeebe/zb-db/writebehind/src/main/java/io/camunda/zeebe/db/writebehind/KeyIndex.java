/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.db.writebehind;

import io.camunda.zeebe.db.DbValue;
import java.util.NavigableSet;
import java.util.concurrent.ConcurrentSkipListSet;
import org.jspecify.annotations.Nullable;

/**
 * Every committed key of one column family, held in memory, so that scans and absent-key lookups of
 * that column family need no storage access. Keys only: values still come from the dirty index, the
 * read cache or the storage.
 *
 * <p>While the state is {@link State#LOADED}: a key is in the index if and only if it has a
 * committed, non-deleted value (unflushed versions count as committed), and each key starts with
 * the column family prefix. In the other states the index is empty.
 *
 * <p>All changes happen under the store's publish lock, write side, and are wrapped in a seqlock:
 * {@link #version} is odd while the key set or the state changes. Readers use no lock. They read
 * {@link #version}, and check it again after each read. A change of an existing key's value doesn't
 * change the key set, so it doesn't bump the version.
 */
final class KeyIndex {

  enum State {
    UNLOADED,
    LOADED,
    DISABLED
  }

  private final long columnFamily;
  private final ConcurrentSkipListSet<RawKey> keys = new ConcurrentSkipListSet<>();
  private volatile State state = State.UNLOADED;
  private volatile long version;

  // the fields below are guarded by the publish lock's write side
  // ConcurrentSkipListSet#size is O(n)
  private int size;
  private boolean changing;
  private long lastCommitSeq;
  private int discardedLoads;

  KeyIndex(final long columnFamily) {
    this.columnFamily = columnFamily;
  }

  long columnFamily() {
    return columnFamily;
  }

  State state() {
    return state;
  }

  long version() {
    return version;
  }

  /** Whether a change of the key set or the state is under way. */
  boolean isChanging() {
    return isChanging(version);
  }

  /**
   * Whether the keys can be read for a reader that read {@code observedVersion} before it called
   * this method: no change was under way then, and the index is loaded. The reader must still check
   * that {@link #version()} is unchanged after it read the keys.
   */
  boolean isStable(final long observedVersion) {
    return !isChanging(observedVersion) && state == State.LOADED;
  }

  private static boolean isChanging(final long version) {
    return (version & 1) != 0;
  }

  /** The keys in order. Only read it while the state is loaded and the version is unchanged. */
  NavigableSet<RawKey> keys() {
    return keys;
  }

  int size() {
    return size;
  }

  long lastCommitSeq() {
    return lastCommitSeq;
  }

  /** Records that a commit wrote to this column family, whatever the state of the index. */
  void recordCommit(final long seq) {
    lastCommitSeq = seq;
  }

  /** Counts a load that a commit made obsolete; returns how many in a row there were. */
  int recordDiscardedLoad() {
    return ++discardedLoads;
  }

  /**
   * Applies the entries {@code from} (inclusive) to {@code to} (exclusive) of a commit that belong
   * to this column family: a value adds the key and a null value removes it. Opens a change window
   * on the first real change and closes it before it returns, also if a change fails.
   */
  void apply(final RawKey[] commitKeys, final @Nullable DbValue[] values, int from, final int to) {
    try {
      for (; from < to; from++) {
        final var key = commitKeys[from];
        final boolean present = values[from] != null;
        if (present != keys.contains(key)) {
          beginChange();
          if (present) {
            keys.add(key);
            size++;
          } else {
            keys.remove(key);
            size--;
          }
        }
      }
    } finally {
      endChange();
    }
  }

  /** Makes the loaded keys visible and the index usable. */
  void install(final Iterable<RawKey> loaded) {
    beginChange();
    for (final var key : loaded) {
      keys.add(key);
      size++;
    }
    state = State.LOADED;
    endChange();
  }

  /** Drops the keys for good; a disabled index stays disabled. */
  void disable() {
    beginChange();
    state = State.DISABLED;
    keys.clear();
    size = 0;
    endChange();
  }

  private void endChange() {
    if (changing) {
      version++;
      changing = false;
    }
  }

  private void beginChange() {
    if (!changing) {
      version++;
      changing = true;
    }
  }
}
