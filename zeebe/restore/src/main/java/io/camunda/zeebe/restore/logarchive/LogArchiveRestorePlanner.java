/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore.logarchive;

import io.camunda.zeebe.backup.logarchive.model.ArchivedSnapshotJson;
import io.camunda.zeebe.backup.logarchive.model.CoveredCheckpointJson;
import io.camunda.zeebe.backup.logarchive.model.LogObjectJson;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Chooses the snapshot and the objects to restore one partition to a checkpoint (spec section 13,
 * steps 3 and 4).
 */
public final class LogArchiveRestorePlanner {

  private LogArchiveRestorePlanner() {}

  /**
   * @throws LogArchiveRestoreException if no contiguous range of objects leads from the snapshot to
   *     the checkpoint
   */
  public static LogArchiveRestorePlan plan(
      final LogArchiveView view, final CoveredCheckpointJson checkpoint) {
    return plan(view, checkpoint, Optional.empty());
  }

  /**
   * Like {@link #plan(LogArchiveView, CoveredCheckpointJson)}, but the journal also contains the
   * log after the given exported position: it starts at the object holding {@code exportedPosition
   * + 1} if that is before the snapshot, so an exporter that is at {@code exportedPosition} resumes
   * without a gap.
   *
   * @param exportedPosition the position up to which the secondary storage already exported, empty
   *     if the journal only has to start at the snapshot
   * @throws LogArchiveRestoreException if no contiguous range of objects leads from the snapshot or
   *     the exported position to the checkpoint, or the checkpoint is below the exported position
   */
  public static LogArchiveRestorePlan plan(
      final LogArchiveView view,
      final CoveredCheckpointJson checkpoint,
      final Optional<Long> exportedPosition) {
    final long position = checkpoint.checkpointPosition();
    if (position < 0 || position > view.archivedPosition()) {
      throw failure(
          view,
          "checkpoint %d at position %d is not covered by the archived position %d"
              .formatted(checkpoint.checkpointId(), position, view.archivedPosition()));
    }
    return plan(view, checkpoint, requiredPosition(view, checkpoint, exportedPosition));
  }

  /**
   * The archive part of a bridged restore (spec section 18.5a): like {@link #plan(LogArchiveView,
   * CoveredCheckpointJson, Optional)}, but the objects start at the first object of the timeline,
   * because old backups provide the log before it. The snapshot still comes from the archive.
   *
   * @param exportedPosition the position the RDBMS exported up to; the checkpoint must not be below
   *     it
   * @throws LogArchiveRestoreException if no contiguous range of objects leads from the start of
   *     the timeline to the checkpoint, the archived snapshot is not covered by it, or the
   *     checkpoint is below the exported position
   */
  public static LogArchiveRestorePlan planBridged(
      final LogArchiveView view,
      final CoveredCheckpointJson checkpoint,
      final long exportedPosition) {
    if (checkpoint.checkpointPosition() < exportedPosition) {
      throw failure(
          view,
          "checkpoint %d at position %d is below the exported position %d"
              .formatted(
                  checkpoint.checkpointId(), checkpoint.checkpointPosition(), exportedPosition));
    }
    final var plan = plan(view, checkpoint, Long.MIN_VALUE);
    if (plan.firstIndex() != view.startIndex()) {
      throw failure(
          view,
          "the objects start at index %d, not at the start of the timeline %d"
              .formatted(plan.firstIndex(), view.startIndex()));
    }
    return plan;
  }

  /**
   * @param requiredPosition the first position the journal must contain: {@link Long#MAX_VALUE} if
   *     only the snapshot matters, {@link Long#MIN_VALUE} to start at the first object
   */
  private static LogArchiveRestorePlan plan(
      final LogArchiveView view,
      final CoveredCheckpointJson checkpoint,
      final long requiredPosition) {
    final long position = checkpoint.checkpointPosition();
    if (position < 0 || position > view.archivedPosition()) {
      throw failure(
          view,
          "checkpoint %d at position %d is not covered by the archived position %d"
              .formatted(checkpoint.checkpointId(), position, view.archivedPosition()));
    }
    final var snapshot = selectSnapshot(view, position);
    final long snapshotIndex = snapshot.map(ArchivedSnapshotJson::index).orElse(0L);
    if (view.hasReleased() && snapshotIndex <= view.releasedIndex()) {
      throw failure(
          view,
          "restore point checkpoint %d is below the restore floor of partition %d: its snapshot is at index %d, the log up to index %d was released"
              .formatted(
                  checkpoint.checkpointId(),
                  view.partitionId(),
                  snapshotIndex,
                  view.releasedIndex()));
    }
    final var objects = selectObjects(view, snapshotIndex, requiredPosition, checkpoint);
    return new LogArchiveRestorePlan(view.partitionId(), checkpoint, snapshot, objects);
  }

  /**
   * The first position the journal must contain, {@link Long#MAX_VALUE} if only the snapshot
   * matters. An exported position of -1 means nothing was exported, so the log is needed from
   * position 1, as for backups.
   */
  private static long requiredPosition(
      final LogArchiveView view,
      final CoveredCheckpointJson checkpoint,
      final Optional<Long> exportedPosition) {
    if (exportedPosition.isEmpty()) {
      return Long.MAX_VALUE;
    }
    final long exported = exportedPosition.get();
    if (checkpoint.checkpointPosition() < exported) {
      throw failure(
          view,
          "checkpoint %d at position %d is below the exported position %d"
              .formatted(checkpoint.checkpointId(), checkpoint.checkpointPosition(), exported));
    }
    final long required = Math.max(exported + 1, 1);
    final long firstArchived = view.firstArchivedPosition();
    if (view.startIndex() > 1 && (firstArchived < 0 || firstArchived > required)) {
      throw failure(
          view,
          "the archive starts at position %d (index %d), after the exported position %d + 1"
              .formatted(firstArchived, view.startIndex(), exported));
    }
    // the checkpoint's object is always replayed; never ask for more than that
    return Math.min(required, checkpoint.checkpointPosition());
  }

  /**
   * The newest snapshot (highest index, then latest archived) whose processed, last follow-up event
   * and max exported positions are all below the checkpoint position, as for backups.
   */
  static Optional<ArchivedSnapshotJson> selectSnapshot(
      final LogArchiveView view, final long checkpointPosition) {
    @Nullable ArchivedSnapshotJson selected = null;
    for (final var snapshot : view.snapshots()) {
      if (snapshot.processedPosition() < checkpointPosition
          && snapshot.lastFollowupEventPosition() < checkpointPosition
          && snapshot.maxExportedPosition() < checkpointPosition
          && (selected == null || snapshot.index() >= selected.index())) {
        selected = snapshot;
      }
    }
    return Optional.ofNullable(selected);
  }

  private static List<LogObjectJson> selectObjects(
      final LogArchiveView view,
      final long snapshotIndex,
      final long requiredPosition,
      final CoveredCheckpointJson checkpoint) {
    final long position = checkpoint.checkpointPosition();
    final var selected = new ArrayList<LogObjectJson>();

    // merged objects replace everything up to M; chunks and open objects only count above it
    for (final var object : view.mergedObjects()) {
      if (select(view, selected, object, snapshotIndex, requiredPosition, checkpoint)) {
        return selected;
      }
    }
    final long mergedIndex = view.mergedIndex();
    for (final var chunk : view.chunks()) {
      if (chunk.lastIndex() <= mergedIndex
          || (chunk.lastIndex() < snapshotIndex && chunk.lastPosition() < requiredPosition)) {
        continue; // merged, or entirely covered by the snapshot and exported; do not read the file
      }
      for (final var object : view.readChunk(chunk).objects()) {
        if (object.lastIndex() <= mergedIndex) {
          continue;
        }
        if (select(view, selected, object, snapshotIndex, requiredPosition, checkpoint)) {
          return selected;
        }
      }
    }
    for (final var object : view.openObjects()) {
      if (object.lastIndex() <= mergedIndex) {
        continue;
      }
      if (select(view, selected, object, snapshotIndex, requiredPosition, checkpoint)) {
        return selected;
      }
    }

    if (selected.isEmpty()) {
      throw failure(
          view,
          "no archived object covers the snapshot index %d (archive starts at index %d)"
              .formatted(snapshotIndex, view.startIndex()));
    }
    throw failure(
        view,
        "no archived object contains checkpoint %d at position %d (archived up to index %d, position %d)"
            .formatted(
                checkpoint.checkpointId(),
                position,
                view.archivedIndex(),
                view.archivedPosition()));
  }

  /**
   * Adds the object if needed; returns true once the object containing the checkpoint is added. The
   * first object is the one holding the snapshot index or the required position, whichever comes
   * first, so the journal may start below the snapshot.
   */
  private static boolean select(
      final LogArchiveView view,
      final List<LogObjectJson> selected,
      final LogObjectJson object,
      final long snapshotIndex,
      final long requiredPosition,
      final CoveredCheckpointJson checkpoint) {
    if (selected.isEmpty()) {
      if (object.lastIndex() < snapshotIndex && object.lastPosition() < requiredPosition) {
        return false;
      }
      if (object.firstIndex() > snapshotIndex + 1) {
        throw failure(
            view,
            snapshotIndex == 0
                ? "there is no snapshot below checkpoint %d and the archive starts at index %d, not 1"
                    .formatted(checkpoint.checkpointId(), object.firstIndex())
                : "no archived object covers the snapshot index %d, the first one after it starts at index %d"
                    .formatted(snapshotIndex, object.firstIndex()));
      }
    } else {
      final var previous = selected.getLast();
      if (object.firstIndex() != previous.lastIndex() + 1) {
        throw failure(
            view,
            "gap in the archived index range: %s ends at index %d but %s starts at index %d"
                .formatted(
                    previous.key(), previous.lastIndex(), object.key(), object.firstIndex()));
      }
    }

    final long position = checkpoint.checkpointPosition();
    if (object.firstPosition() > position) {
      throw failure(
          view,
          "object %s starts at position %d, past checkpoint %d at position %d, without containing it"
              .formatted(
                  object.key(), object.firstPosition(), checkpoint.checkpointId(), position));
    }
    selected.add(object);
    return object.firstPosition() >= 0 && position <= object.lastPosition();
  }

  private static LogArchiveRestoreException failure(
      final LogArchiveView view, final String detail) {
    return new LogArchiveRestoreException(
        "Cannot restore partition %d from timeline '%s': %s"
            .formatted(view.partitionId(), view.timelineId(), detail));
  }
}
