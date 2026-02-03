/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.api;

import java.util.Objects;
import java.util.SequencedCollection;
import java.util.Set;
import java.util.stream.Stream;

public sealed interface BackupRange {

  boolean contains(Interval<Long> other);

  /** Returns the checkpoints range extremes in order */
  SequencedCollection<Long> checkpoints();

  /** A complete backup range without deletions. */
  record Complete(Interval<Long> interval) implements BackupRange {
    public Complete(final long startCheckpointId, final long endCheckpointId) {
      this(new Interval<>(startCheckpointId, endCheckpointId));
    }

    @Override
    public boolean contains(final Interval<Long> other) {
      return interval().contains(other);
    }

    @Override
    public SequencedCollection<Long> checkpoints() {
      return interval.values();
    }
  }

  /**
   * A backup range with deletions. Verification of all contained backups is required to determine
   * whether the range is effectively complete or not.
   */
  record Incomplete(Interval<Long> interval, Set<Long> deletedCheckpointIds)
      implements BackupRange {
    public Incomplete(
        final long startCheckpointId,
        final long endCheckpointId,
        final Set<Long> deletedCheckpointIds) {
      this(new Interval<>(startCheckpointId, endCheckpointId), deletedCheckpointIds);
    }

    public Incomplete {
      Objects.requireNonNull(deletedCheckpointIds, "deletedCheckpointIds must not be null");
      if (deletedCheckpointIds.isEmpty()) {
        throw new IllegalArgumentException("deletedCheckpointIds must not be empty");
      }
      deletedCheckpointIds = Set.copyOf(deletedCheckpointIds);
    }

    @Override
    public boolean contains(final Interval<Long> other) {
      return interval.contains(other) && !isInDeletionRange(other);
    }

    @Override
    public SequencedCollection<Long> checkpoints() {
      return Stream.concat(interval.values().stream(), deletedCheckpointIds.stream())
          .sorted()
          .distinct()
          .toList();
    }

    private boolean isInDeletionRange(final Interval<Long> interval) {
      return deletedCheckpointIds.stream().anyMatch(interval::contains);
    }
  }
}
