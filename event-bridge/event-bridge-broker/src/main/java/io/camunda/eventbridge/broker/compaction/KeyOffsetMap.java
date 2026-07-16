/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.compaction;

import io.camunda.eventbridge.broker.compaction.KeyHash.Hash128;
import java.util.HashMap;
import java.util.Map;

/**
 * A bounded map from a 128-bit key hash to the latest log position seen for that key over a single
 * cleaner pass's dirty range. It is the cleaner's decision oracle: during the sweep a record is
 * kept only if its position equals the latest recorded for its key (ADR 0001, decision 6.3).
 *
 * <h3>Fill order and overflow</h3>
 *
 * <p>The cleaner feeds positions in ascending order via {@link #recordLatest}. Updating a key
 * already present always succeeds. Inserting a <em>new</em> key once the map is at capacity fails —
 * the map records that it overflowed and at which position. The caller must then stop reading and
 * lower the cleaner point to {@link #highestFitPosition()} (the last position fully absorbed),
 * sweep that reduced range, and let a later pass finish the rest. Multi-pass compaction of a range
 * converges to the same result a single unbounded pass would produce.
 *
 * <p>Because inserts arrive in ascending position order, every key with a position at or below
 * {@link #highestFitPosition()} is fully tracked at overflow time, so cutting there is safe: no key
 * in the swept sub-range can have a later occurrence that the map missed.
 *
 * <h3>The hash key</h3>
 *
 * <p>Keys are identified by their {@link KeyHash} 128-bit digest, not their bytes — see {@link
 * KeyHash} for the collision rationale. The bound is on the number of distinct keys, which caps the
 * map's heap footprint independent of key size.
 *
 * <p>Threading: not thread-safe; owned and driven by a single cleaner pass on the cleaner actor.
 */
public final class KeyOffsetMap {

  /** Returned by {@link #latest} when the key has no recorded position. */
  public static final long NO_ENTRY = -1L;

  private final int capacity;
  private final Map<Hash128, Long> latestByKey;
  private long highestFitPosition = NO_ENTRY;
  private boolean overflowed;
  private long overflowPosition = NO_ENTRY;

  /**
   * @param capacity the maximum number of distinct keys the map will hold before overflowing (must
   *     be positive)
   */
  public KeyOffsetMap(final int capacity) {
    if (capacity <= 0) {
      throw new IllegalArgumentException("capacity must be positive, was " + capacity);
    }
    this.capacity = capacity;
    // Size the backing map to avoid resize churn while still respecting the logical bound.
    latestByKey = new HashMap<>(Math.min(capacity, 1 << 16));
  }

  /**
   * Records that {@code position} is the latest seen for {@code key} so far. Updating a key already
   * present always succeeds. Inserting a new key when the map is full fails and marks the map
   * overflowed.
   *
   * <p>Callers must feed positions in ascending order and must stop feeding on the first {@code
   * false} return, so that {@link #highestFitPosition()} is the highest fully-absorbed position.
   *
   * @param key the 128-bit key digest
   * @param position the record's log position
   * @return {@code true} if recorded; {@code false} if the map is full and this is a new key
   */
  public boolean recordLatest(final Hash128 key, final long position) {
    final Long existing = latestByKey.get(key);
    if (existing != null) {
      if (position > existing) {
        latestByKey.put(key, position);
      }
      if (position > highestFitPosition) {
        highestFitPosition = position;
      }
      return true;
    }
    if (latestByKey.size() >= capacity) {
      overflowed = true;
      if (overflowPosition == NO_ENTRY) {
        overflowPosition = position;
      }
      return false;
    }
    latestByKey.put(key, position);
    if (position > highestFitPosition) {
      highestFitPosition = position;
    }
    return true;
  }

  /**
   * Returns the latest recorded position for {@code key}, or {@link #NO_ENTRY} if the key was never
   * recorded (i.e. it does not appear in this pass's dirty range).
   */
  public long latest(final Hash128 key) {
    final Long p = latestByKey.get(key);
    return p == null ? NO_ENTRY : p;
  }

  /** Returns {@code true} if a new-key insert was rejected because the map was full. */
  public boolean overflowed() {
    return overflowed;
  }

  /**
   * Returns the highest position that was fully absorbed before any overflow — the cleaner point
   * the caller should fall back to when {@link #overflowed()} is {@code true}. Returns {@link
   * #NO_ENTRY} if nothing was recorded.
   */
  public long highestFitPosition() {
    return highestFitPosition;
  }

  /**
   * Returns the position of the record that first triggered overflow, or {@link #NO_ENTRY} if the
   * map never overflowed. Always strictly greater than {@link #highestFitPosition()} when set.
   */
  public long overflowPosition() {
    return overflowPosition;
  }

  /** Returns the number of distinct keys currently tracked. */
  public int size() {
    return latestByKey.size();
  }
}
