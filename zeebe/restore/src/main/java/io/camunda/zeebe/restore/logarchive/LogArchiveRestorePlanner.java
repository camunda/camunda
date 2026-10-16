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
    final long position = checkpoint.checkpointPosition();
    if (position < 0 || position > view.archivedPosition()) {
      throw failure(
          view,
          "checkpoint %d at position %d is not covered by the archived position %d"
              .formatted(checkpoint.checkpointId(), position, view.archivedPosition()));
    }

    final var snapshot = selectSnapshot(view, position);
    final long snapshotIndex = snapshot.map(ArchivedSnapshotJson::index).orElse(0L);
    final var objects = selectObjects(view, snapshotIndex, checkpoint);
    return new LogArchiveRestorePlan(view.partitionId(), checkpoint, snapshot, objects);
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
      final LogArchiveView view, final long snapshotIndex, final CoveredCheckpointJson checkpoint) {
    final long position = checkpoint.checkpointPosition();
    final var selected = new ArrayList<LogObjectJson>();

    for (final var chunk : view.chunks()) {
      if (chunk.lastIndex() < snapshotIndex) {
        continue; // entirely covered by the snapshot; do not read the chunk file
      }
      for (final var object : view.readChunk(chunk).objects()) {
        if (select(view, selected, object, snapshotIndex, checkpoint)) {
          return selected;
        }
      }
    }
    for (final var object : view.openObjects()) {
      if (select(view, selected, object, snapshotIndex, checkpoint)) {
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

  /** Adds the object if needed; returns true once the object containing the checkpoint is added. */
  private static boolean select(
      final LogArchiveView view,
      final List<LogObjectJson> selected,
      final LogObjectJson object,
      final long snapshotIndex,
      final CoveredCheckpointJson checkpoint) {
    if (selected.isEmpty()) {
      if (object.lastIndex() < snapshotIndex) {
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
