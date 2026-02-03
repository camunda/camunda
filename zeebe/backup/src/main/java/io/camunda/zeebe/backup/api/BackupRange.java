/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.api;

import java.util.Objects;
import java.util.Set;

public sealed interface BackupRange {

  boolean contains(Interval other);

  /**
   * Represents a range of checkpoint IDs.
   *
   * @param start the first checkpoint ID in the range (inclusive)
   * @param end the last checkpoint ID in the range (inclusive)
   */
  record Interval(long start, long end) {
    /**
     * @param other the interval to check
     * @return true if this interval completely covers the other interval
     */
    public boolean contains(final Interval other) {
      return start <= other.start && end >= other.end;
    }

    /**
     * @param index the checkpoint ID to check
     * @return true if the checkpoint ID is within this interval
     */
    public boolean contains(final long index) {
      return index >= start && index <= end;
    }
  }

  /** A complete backup range without deletions. */
  record Complete(Interval interval) implements BackupRange {
    public Complete(final long startCheckpointId, final long endCheckpointId) {
      this(new Interval(startCheckpointId, endCheckpointId));
    }

    @Override
    public boolean contains(final Interval other) {
      return interval().contains(other);
    }
  }

  /**
   * A backup range with deletions. Verification of all contained backups is required to determine
   * whether the range is effectively complete or not.
   */
  record Incomplete(Interval interval, Set<Long> deletedCheckpointIds) implements BackupRange {
    public Incomplete(
        final long startCheckpointId,
        final long endCheckpointId,
        final Set<Long> deletedCheckpointIds) {
      this(new Interval(startCheckpointId, endCheckpointId), deletedCheckpointIds);
    }

    public Incomplete {
      Objects.requireNonNull(deletedCheckpointIds, "deletedCheckpointIds must not be null");
      if (deletedCheckpointIds.isEmpty()) {
        throw new IllegalArgumentException("deletedCheckpointIds must not be empty");
      }
      deletedCheckpointIds = Set.copyOf(deletedCheckpointIds);
    }

    @Override
    public boolean contains(final Interval other) {
      return interval.contains(other) && !isInDeletionRange(other);
    }

    private boolean isInDeletionRange(final Interval interval) {
      return deletedCheckpointIds.stream().anyMatch(interval::contains);
    }
  }
}
