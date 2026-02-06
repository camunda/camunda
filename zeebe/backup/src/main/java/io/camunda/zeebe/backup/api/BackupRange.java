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
  long firstCheckpointId();

  long lastCheckpointId();

  boolean contains(Interval<Long> other);

  /** Returns the checkpoints range extremes in order */
  SequencedCollection<Long> checkpoints();

  /** A complete backup range without deletions. */
  record Complete(Interval<Long> checkpointInterval) implements BackupRange {
    public Complete(final long startCheckpointId, final long endCheckpointId) {
      this(new Interval<>(startCheckpointId, endCheckpointId));
    }

    @Override
    public long firstCheckpointId() {
      return checkpointInterval().start();
    }

    @Override
    public long lastCheckpointId() {
      return checkpointInterval.end();
    }

    @Override
    public boolean contains(final Interval<Long> other) {
      return checkpointInterval().contains(other);
    }

    @Override
    public SequencedCollection<Long> checkpoints() {
      return checkpointInterval.values();
    }
  }

  /**
   * A backup range with deletions. Verification of all contained backups is required to determine
   * whether the range is effectively complete or not.
   */
  record Incomplete(Interval<Long> checkpontInterval, Set<Long> deletedCheckpointIds)
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

    private boolean isInDeletionRange(final Interval<Long> interval) {
      return deletedCheckpointIds.stream().anyMatch(interval::contains);
    }

    @Override
    public long firstCheckpointId() {
      return checkpontInterval().start();
    }

    @Override
    public long lastCheckpointId() {
      return checkpontInterval.end();
    }

    @Override
    public boolean contains(final Interval<Long> other) {
      return checkpontInterval.contains(other) && !isInDeletionRange(other);
    }

    @Override
    public SequencedCollection<Long> checkpoints() {
      return Stream.concat(checkpontInterval.values().stream(), deletedCheckpointIds.stream())
          .sorted()
          .distinct()
          .toList();
    }
  }
}
