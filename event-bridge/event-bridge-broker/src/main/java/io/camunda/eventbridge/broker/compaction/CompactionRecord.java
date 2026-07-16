/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.broker.compaction;

import java.util.Arrays;

/**
 * The unit of work the cleaner reasons about: a single record with its stable log position, its
 * broker append timestamp, its optional key, its value, and whether the batch it came from declared
 * the {@code KEYED} attribute (event-bridge ADR 0001).
 *
 * <p>This is the common shape flowing through the whole compaction library. The {@link
 * DirtyLogReader} produces it from the raw journal; the {@link CleanSegmentReader} produces it from
 * a previously-written clean segment; the {@link CleanSegmentWriter} consumes it, re-wrapping each
 * surviving record as its own single-entry batch. Positions are never renumbered — a dropped record
 * simply leaves a gap.
 *
 * <h3>Keys, tombstones and the {@code KEYED} attribute</h3>
 *
 * <ul>
 *   <li>{@link #hasKey()} — a record participates in latest-per-key retention only if it carries a
 *       key. Key-less records are copied forward verbatim and are never compacted (see the
 *       un-keyed-record note in {@link CompactionPass}).
 *   <li>{@link #isTombstone()} — a keyed record with an empty value; the deletion marker.
 *   <li>{@link #keyed()} — the batch-level {@code KEYED} attribute, preserved so a rewritten clean
 *       segment is byte-faithful to the source batch's declaration.
 * </ul>
 *
 * <p>Threading: an immutable value carrier. The {@code key}/{@code value} arrays are treated as
 * owned by this record and must not be mutated by callers after construction.
 *
 * @param position the stable log position of the record (never renumbered)
 * @param timestamp the broker append timestamp in millis (preserved verbatim)
 * @param key the key bytes, or an empty array when the record has no key (never {@code null})
 * @param value the value bytes; empty on a keyed record denotes a tombstone (never {@code null})
 * @param keyed whether the source batch declared the {@code KEYED} attribute
 */
public record CompactionRecord(
    long position, long timestamp, byte[] key, byte[] value, boolean keyed) {

  public CompactionRecord {
    if (key == null) {
      throw new IllegalArgumentException("key must not be null (use an empty array for no key)");
    }
    if (value == null) {
      throw new IllegalArgumentException("value must not be null (use an empty array)");
    }
  }

  /** Returns {@code true} if this record carries a key (and so can participate in compaction). */
  public boolean hasKey() {
    return key.length > 0;
  }

  /**
   * Returns {@code true} if this record is a tombstone: a keyed record with an empty value — the
   * deletion marker for latest-per-key retention.
   */
  public boolean isTombstone() {
    return hasKey() && value.length == 0;
  }

  @Override
  public boolean equals(final Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof final CompactionRecord other)) {
      return false;
    }
    return position == other.position
        && timestamp == other.timestamp
        && keyed == other.keyed
        && Arrays.equals(key, other.key)
        && Arrays.equals(value, other.value);
  }

  @Override
  public int hashCode() {
    int result = Long.hashCode(position);
    result = 31 * result + Long.hashCode(timestamp);
    result = 31 * result + Boolean.hashCode(keyed);
    result = 31 * result + Arrays.hashCode(key);
    result = 31 * result + Arrays.hashCode(value);
    return result;
  }

  @Override
  public String toString() {
    return "CompactionRecord[position="
        + position
        + ", timestamp="
        + timestamp
        + ", keyLength="
        + key.length
        + ", valueLength="
        + value.length
        + ", tombstone="
        + isTombstone()
        + ", keyed="
        + keyed
        + "]";
  }
}
