/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.eventbridge.streaming.changelog;

/**
 * One keyed record of a shard's changelog output (ADR 0009 Decisions 1/2): a put (the row's
 * serialized bytes — the exact bytes the store's own {@code persistFrozen()} writes) or a tombstone
 * (an empty value, event-bridge ADR 0001's convention for a keyed delete on a compacted topic).
 * {@code key} is the cell's store key bytes, unchanged — it already carries the store's ownership
 * prefix (e.g. a {@code GroupedCellStore}'s {@code group ++ windowStart ++ codec(key)} framing), so
 * the changelog partition's keyspace is exactly the local store's.
 */
public record ChangelogRecord(byte[] key, byte[] value) {

  private static final byte[] EMPTY = new byte[0];

  public ChangelogRecord {
    if (key == null || key.length == 0) {
      throw new IllegalArgumentException("expected a non-empty changelog record key");
    }
    if (value == null) {
      throw new IllegalArgumentException(
          "expected a non-null changelog record value; use tombstone(key) for a delete");
    }
  }

  /** A put: {@code key}'s row is {@code value} (the exact bytes the store persists). */
  public static ChangelogRecord put(final byte[] key, final byte[] value) {
    if (value.length == 0) {
      throw new IllegalArgumentException(
          "a zero-length value is reserved for tombstones; a put must carry real bytes");
    }
    return new ChangelogRecord(key, value);
  }

  /**
   * A tombstone: {@code key}'s row is deleted (an empty value, event-bridge ADR 0001's keyed-delete
   * convention on a compacted topic).
   */
  public static ChangelogRecord tombstone(final byte[] key) {
    return new ChangelogRecord(key, EMPTY);
  }

  /** Whether this record is a tombstone (an empty value). */
  public boolean isTombstone() {
    return value.length == 0;
  }
}
