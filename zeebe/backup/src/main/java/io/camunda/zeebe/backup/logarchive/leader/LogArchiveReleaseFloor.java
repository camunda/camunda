/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.backup.logarchive.leader;

import io.camunda.zeebe.protocol.impl.record.value.management.ArchivedSnapshot;
import io.camunda.zeebe.protocol.impl.record.value.management.CoveredCheckpoint;
import io.camunda.zeebe.protocol.impl.record.value.management.LogObject;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.jspecify.annotations.NullMarked;

/**
 * Computes the restore floor of a partition from its replicated state (spec section 21.1). A pure
 * function, so that the leader's choice is easy to test; the processor only checks that a proposal
 * is safe.
 */
@NullMarked
public final class LogArchiveReleaseFloor {

  private LogArchiveReleaseFloor() {}

  /**
   * @param backupCheckpoints the covered checkpoints whose type creates a backup
   * @param snapshots the archived snapshots of the current timeline, in archive order
   * @param mergedObjects the merged objects above the released index, in index order
   * @param nowMillis the leader's clock
   * @param retentionWindow restore points younger than this stay restorable
   * @return R, the {@code lastIndex} of the newest merged object that ends below the floor
   *     snapshot; empty if there is no backup, no usable snapshot or no such merged object
   */
  public static OptionalLong releasedIndex(
      final List<CoveredCheckpoint> backupCheckpoints,
      final List<ArchivedSnapshot> snapshots,
      final List<LogObject> mergedObjects,
      final long nowMillis,
      final Duration retentionWindow) {
    final var floorSnapshot =
        floorSnapshot(backupCheckpoints, snapshots, nowMillis, retentionWindow);
    if (floorSnapshot.isEmpty()) {
      return OptionalLong.empty();
    }
    final long floorIndex = floorSnapshot.get().getIndex();
    return mergedObjects.stream()
        .mapToLong(LogObject::getLastIndex)
        .filter(lastIndex -> lastIndex < floorIndex)
        .max();
  }

  /**
   * The floor snapshot S (spec section 21.1): the snapshot restore picks for the protected
   * checkpoint. Shared by the releaser and the hold mover.
   *
   * @return empty if there is no backup checkpoint or no usable snapshot
   */
  public static Optional<ArchivedSnapshot> floorSnapshot(
      final List<CoveredCheckpoint> backupCheckpoints,
      final List<ArchivedSnapshot> snapshots,
      final long nowMillis,
      final Duration retentionWindow) {
    return protectedCheckpoint(backupCheckpoints, nowMillis, retentionWindow)
        .flatMap(c -> floorSnapshot(snapshots, c.getCheckpointPosition()));
  }

  /**
   * The oldest backup checkpoint taken within the window, else the newest one: the newest backup
   * always stays restorable.
   */
  static Optional<CoveredCheckpoint> protectedCheckpoint(
      final List<CoveredCheckpoint> backupCheckpoints,
      final long nowMillis,
      final Duration retentionWindow) {
    final long cutoff = nowMillis - retentionWindow.toMillis();
    final var inWindow =
        backupCheckpoints.stream()
            .filter(c -> c.getCheckpointTimestamp() >= cutoff)
            .min(Comparator.comparingLong(CoveredCheckpoint::getCheckpointId));
    return inWindow.isPresent()
        ? inWindow
        : backupCheckpoints.stream()
            .max(Comparator.comparingLong(CoveredCheckpoint::getCheckpointId));
  }

  /**
   * The snapshot restore picks for a checkpoint (spec section 13, step 3): the newest one whose
   * processed, last follow-up event and max exported positions are all below the position.
   */
  static Optional<ArchivedSnapshot> floorSnapshot(
      final List<ArchivedSnapshot> snapshots, final long checkpointPosition) {
    ArchivedSnapshot selected = null;
    for (final var snapshot : snapshots) {
      if (snapshot.getProcessedPosition() < checkpointPosition
          && snapshot.getLastFollowupEventPosition() < checkpointPosition
          && snapshot.getMaxExportedPosition() < checkpointPosition
          && (selected == null || snapshot.getIndex() >= selected.getIndex())) {
        selected = snapshot;
      }
    }
    return Optional.ofNullable(selected);
  }
}
