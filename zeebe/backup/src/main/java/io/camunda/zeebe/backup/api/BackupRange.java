/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.api;

import io.camunda.zeebe.backup.common.CheckpointIdGenerator;
import java.time.Instant;
import java.util.Objects;
import java.util.Set;

public sealed interface BackupRange {
  long firstCheckpointId();

  long lastCheckpointId();

  boolean contains(Interval<Long> other);

  Interval<Long> checkpointInterval();

  default Interval<Instant> timeInterval(final CheckpointIdGenerator generator) {
    return checkpointInterval().map(generator::toInstant);
  }

  /** A complete backup range without deletions. */
  record Complete(Interval<Long> checkpointInterval) implements BackupRange {
    public Complete(final long startCheckpointId, final long endCheckpointId) {
      this(Interval.closed(startCheckpointId, endCheckpointId));
    }

    @Override
    public long firstCheckpointId() {
      return checkpointInterval().start();
    }

    @Override
    public long lastCheckpointId() {
      return checkpointInterval().end();
    }

    @Override
    public boolean contains(final Interval<Long> other) {
      return checkpointInterval().contains(other);
    }
  }

  /**
   * A backup range with deletions. Verification of all contained backups is required to determine
   * whether the range is effectively complete or not.
   */
  record Incomplete(Interval<Long> checkpointInterval, Set<Long> deletedCheckpointIds)
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
      return checkpointInterval.start();
    }

    @Override
    public long lastCheckpointId() {
      return checkpointInterval.end();
    }

    @Override
    public boolean contains(final Interval<Long> other) {
      return checkpointInterval.contains(other) && !isInDeletionRange(other);
    }
  }
}
