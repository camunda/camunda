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
import java.util.NavigableSet;
import java.util.concurrent.ConcurrentSkipListSet;
import org.jspecify.annotations.Nullable;

/**
 * Every committed key of one column family, held in memory, so that scans and absent-key lookups of
 * that column family need no storage access. Keys only: values still come from the dirty index, the
 * read cache or the storage.
 *
 * <p>While the state is {@link State#LOADED}: a key is in the index if and only if it is inside the
 * window and has a committed, non-deleted value (unflushed versions count as committed), and each
 * key starts with the column family prefix. In the other states the index is empty.
 *
 * <p>The window is the whole column family while {@link #bound()} is null. Else, by the {@link
 * KeyIndexPolicy}, it holds the keys below ({@code KEEP_LOWEST}) or above ({@code KEEP_HIGHEST})
 * the bound, and the index says nothing about the others. The bound changes in the same change
 * window as the key set.
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
  private final KeyIndexPolicy policy;
  private final ConcurrentSkipListSet<RawKey> keys = new ConcurrentSkipListSet<>();
  private volatile State state = State.UNLOADED;
  private volatile long version;
  private volatile @Nullable RawKey bound;

  // the fields below are guarded by the publish lock's write side
  // ConcurrentSkipListSet#size is O(n); volatile so that a scan can read it without the lock
  private volatile int size;
  private boolean changing;
  private long lastCommitSeq;
  private int discardedLoads;

  KeyIndex(final long columnFamily, final KeyIndexPolicy policy) {
    this.columnFamily = columnFamily;
    this.policy = policy;
  }

  KeyIndexPolicy policy() {
    return policy;
  }

  /**
   * The first key that the window doesn't cover, or null if it covers the column family. Read it
   * after {@link #version()} and check the version again afterwards.
   */
  @Nullable RawKey bound() {
    return bound;
  }

  /** Whether the window around {@code bound} covers the key. */
  boolean covers(final @Nullable RawKey bound, final RawKey key) {
    return covers(bound, key.bytes());
  }

  boolean covers(final @Nullable RawKey bound, final byte[] key) {
    if (bound == null) {
      return true;
    }
    final int comparison = Arrays.compareUnsigned(key, bound.bytes());
    return policy.keepsLowest() ? comparison < 0 : comparison > 0;
  }

  /**
   * Whether a scan in the given direction that runs out of window continues with the storage at the
   * bound: the covered keys are the first ones in that direction.
   */
  boolean handsOver(final boolean forward) {
    return policy.keepsLowest() == forward;
  }

  /** Whether the window is so empty that a scan should extend it. */
  boolean needsRefill(final int maxKeys) {
    return state == State.LOADED && bound != null && size < maxKeys / 4;
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
   * on the first real change and closes it before it returns, also if a change fails. Keys outside
   * the window are skipped. A windowed policy then evicts the keys at the far end of the window
   * while there are more than {@code maxKeys}, and moves the bound to the last one evicted.
   */
  void apply(
      final RawKey[] commitKeys,
      final @Nullable DbValue[] values,
      int from,
      final int to,
      final int maxKeys) {
    try {
      final var window = bound;
      for (; from < to; from++) {
        final var key = commitKeys[from];
        if (!covers(window, key)) {
          continue;
        }
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
      if (policy != KeyIndexPolicy.DISABLE) {
        while (size > maxKeys) {
          beginChange();
          final var evicted = policy.keepsLowest() ? keys.pollLast() : keys.pollFirst();
          size--;
          bound = evicted;
        }
      }
    } finally {
      endChange();
    }
  }

  /**
   * Makes the loaded keys visible and the index usable. The window covers the keys up to {@code
   * bound}; null if the load reached the end of the column family.
   */
  void install(final Iterable<RawKey> loaded, final @Nullable RawKey bound) {
    beginChange();
    this.bound = bound;
    for (final var key : loaded) {
      keys.add(key);
      size++;
    }
    state = State.LOADED;
    endChange();
  }

  /**
   * Adds keys beyond the bound, away from the covered end, and moves the bound to {@code newBound};
   * null if they reach the end of the column family.
   */
  void extend(final Iterable<RawKey> more, final @Nullable RawKey newBound) {
    beginChange();
    bound = newBound;
    for (final var key : more) {
      if (keys.add(key)) {
        size++;
      }
    }
    discardedLoads = 0;
    endChange();
  }

  /** Drops the keys for good; a disabled index stays disabled. */
  void disable() {
    beginChange();
    state = State.DISABLED;
    bound = null;
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
