/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import io.camunda.zeebe.backup.common.BackupMetadata;
import io.camunda.zeebe.backup.common.BackupMetadata.CheckpointEntry;
import io.camunda.zeebe.backup.common.BackupMetadata.RangeEntry;
import io.camunda.zeebe.dynamic.config.state.PartitionRestore;
import io.camunda.zeebe.restore.RestorePointResolver.RestorableBackups;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.TreeSet;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A brute-force model of the restore point a time-range restore resolves to, written from the
 * contract of {@link RestorePointResolver} and its example tests rather than from its algorithm. It
 * enumerates every checkpoint, keeps those all partitions can be restored to, and then builds each
 * partition's restore from the rules below:
 *
 * <ul>
 *   <li>A partition restores from the oldest of its backup ranges that holds {@code from} in time
 *       and the exported position in its log. Its log spans from the first log position of the
 *       range's first backup to the position of its last backup, and every checkpoint in that span
 *       can be restored. An exported position of -1 means nothing was exported, so the log has to
 *       hold position 1.
 *   <li>A checkpoint is a restore point if every partition can restore it, has a backup at or
 *       before it, and records it at or after the partition's exported position. Without {@code
 *       to}, the latest restore point is chosen; with it, the one closest to {@code to} as the
 *       first partition recorded it, the earlier one on a tie.
 *   <li>A partition restores the backups of its log up to the first one at or after the restore
 *       point, and they have to be gap free. It starts from the first backup at or after {@code
 *       from}, but no later than the last backup at or before the restore point; without {@code
 *       from}, from the last backup at or before the exported position, else the first. With both,
 *       the starting backup has to be at or before the exported position.
 *   <li>The restored log ends at the restore point, so it has to lie after the second to last
 *       backup, or be the only backup itself.
 * </ul>
 */
@NullMarked
final class RestorePointModel {

  private RestorePointModel() {}

  static Resolution resolve(
      final List<BackupMetadata> metadata,
      final @Nullable Instant from,
      final @Nullable Instant to,
      final @Nullable Map<Integer, Long> exportedPositions) {
    final var restorable = new HashMap<Integer, List<CheckpointEntry>>();
    for (final var partition : metadata) {
      final var exported = exportedPosition(exportedPositions, partition.partitionId());
      final var log =
          partition.ranges().stream()
              .map(range -> RangeLog.of(partition, range))
              .flatMap(Optional::stream)
              .filter(range -> range.holds(from, exported))
              .findFirst();
      if (log.isEmpty()) {
        return new Rejected(
            Reason.NO_RANGE,
            "no range of partition %d holds from=%s and exported position %s"
                .formatted(partition.partitionId(), from, exported));
      }
      restorable.put(
          partition.partitionId(),
          partition.checkpoints().stream()
              .filter(checkpoint -> log.get().covers(checkpoint.checkpointPosition()))
              .toList());
    }

    final var first = metadata.getFirst().partitionId();
    final var restorePoints =
        restorable.get(first).stream()
            .filter(
                candidate ->
                    metadata.stream()
                        .allMatch(
                            partition ->
                                canRestoreTo(
                                    restorable.get(partition.partitionId()),
                                    candidate.checkpointId(),
                                    exportedPosition(exportedPositions, partition.partitionId()))))
            .toList();
    if (restorePoints.isEmpty()) {
      return new Rejected(
          Reason.NO_RESTORE_POINT, "no checkpoint can be restored on every partition");
    }
    final var restorePoint =
        to == null
            ? restorePoints.stream().max(Comparator.comparingLong(CheckpointEntry::checkpointId))
            : restorePoints.stream()
                .min(
                    Comparator.<CheckpointEntry, Duration>comparing(
                            checkpoint ->
                                Duration.between(to, checkpoint.checkpointTimestamp()).abs())
                        .thenComparingLong(CheckpointEntry::checkpointId));
    final var checkpointId = restorePoint.orElseThrow().checkpointId();

    final var partitions = new HashMap<Integer, PartitionRestore>();
    for (final var partition : metadata) {
      final var partitionId = partition.partitionId();
      final var restore =
          restorePartition(
              restorable.get(partitionId),
              checkpointId,
              from,
              exportedPosition(exportedPositions, partitionId));
      switch (restore) {
        case final Rejected rejected -> {
          return new Rejected(
              rejected.reason(), "partition %d: %s".formatted(partitionId, rejected.detail()));
        }
        case final PartitionResolved resolved -> partitions.put(partitionId, resolved.restore());
      }
    }
    return new Resolved(new RestorableBackups(checkpointId, partitions));
  }

  private static boolean canRestoreTo(
      final List<CheckpointEntry> restorable,
      final long checkpointId,
      final @Nullable Long exportedPosition) {
    final var checkpoint = find(restorable, checkpointId);
    return checkpoint.isPresent()
        && backups(restorable).stream().anyMatch(backup -> backup.checkpointId() <= checkpointId)
        && (exportedPosition == null || checkpoint.get().checkpointPosition() >= exportedPosition);
  }

  private static PartitionResolution restorePartition(
      final List<CheckpointEntry> restorable,
      final long checkpointId,
      final @Nullable Instant from,
      final @Nullable Long exportedPosition) {
    final var restorePoint = find(restorable, checkpointId).orElseThrow();
    final var backups = backups(restorable);
    final var lastBackup =
        backups.stream().filter(backup -> backup.checkpointId() >= checkpointId).findFirst();
    if (lastBackup.isEmpty()) {
      return new Rejected(
          Reason.NO_BACKUP_AFTER, "no backup at or after checkpoint " + checkpointId);
    }
    final var needed =
        backups.stream()
            .filter(backup -> backup.checkpointId() <= lastBackup.get().checkpointId())
            .toList();
    for (int i = 1; i < needed.size(); i++) {
      if (needed.get(i).getFirstLogPositionOrDefault()
          > needed.get(i - 1).checkpointPosition() + 1) {
        return new Rejected(
            Reason.GAP,
            "gap between backups %d and %d"
                .formatted(needed.get(i - 1).checkpointId(), needed.get(i).checkpointId()));
      }
    }

    final var lastAtRestorePoint =
        needed.stream().filter(backup -> backup.checkpointId() <= checkpointId).toList().getLast();
    final CheckpointEntry start;
    if (from != null) {
      start =
          needed.stream()
              .filter(
                  backup ->
                      !backup.checkpointTimestamp().isBefore(from) || backup == lastAtRestorePoint)
              .findFirst()
              .orElseThrow();
      if (exportedPosition != null && start.checkpointPosition() > exportedPosition) {
        return new Rejected(
            Reason.START_PAST_EXPORTED,
            "backup %d after from is past the exported position %d"
                .formatted(start.checkpointId(), exportedPosition));
      }
    } else if (exportedPosition != null) {
      final var atOrBeforeExported =
          needed.stream()
              .filter(backup -> backup.checkpointPosition() <= exportedPosition)
              .toList();
      start = atOrBeforeExported.isEmpty() ? needed.getFirst() : atOrBeforeExported.getLast();
    } else {
      start = needed.getFirst();
    }
    final var restored =
        needed.stream().filter(backup -> backup.checkpointId() >= start.checkpointId()).toList();

    final var position = restorePoint.checkpointPosition();
    final var endsAtRestorePoint =
        restored.size() == 1
            ? position == restored.getFirst().checkpointPosition()
            : position > restored.get(restored.size() - 2).checkpointPosition()
                && position <= restored.getLast().checkpointPosition();
    if (!endsAtRestorePoint) {
      return new Rejected(
          Reason.LOG_CANNOT_END,
          "the log restored from backups %s cannot end at position %d"
              .formatted(restored.stream().map(CheckpointEntry::checkpointId).toList(), position));
    }

    final var ids = new TreeSet<Long>();
    restored.forEach(backup -> ids.add(backup.checkpointId()));
    return new PartitionResolved(new PartitionRestore(ids, OptionalLong.of(position)));
  }

  private static List<CheckpointEntry> backups(final List<CheckpointEntry> checkpoints) {
    final var backups = new ArrayList<CheckpointEntry>();
    for (final var checkpoint : checkpoints) {
      if (checkpoint.checkpointType().shouldCreateBackup()) {
        backups.add(checkpoint);
      }
    }
    return backups;
  }

  private static Optional<CheckpointEntry> find(
      final List<CheckpointEntry> checkpoints, final long checkpointId) {
    return checkpoints.stream()
        .filter(checkpoint -> checkpoint.checkpointId() == checkpointId)
        .findFirst();
  }

  private static @Nullable Long exportedPosition(
      final @Nullable Map<Integer, Long> exportedPositions, final int partitionId) {
    return exportedPositions == null ? null : exportedPositions.get(partitionId);
  }

  sealed interface Resolution permits Resolved, Rejected {}

  record Resolved(RestorableBackups backups) implements Resolution {}

  record Rejected(Reason reason, String detail) implements Resolution, PartitionResolution {}

  enum Reason {
    NO_RANGE,
    NO_RESTORE_POINT,
    NO_BACKUP_AFTER,
    GAP,
    START_PAST_EXPORTED,
    LOG_CANNOT_END
  }

  private sealed interface PartitionResolution permits PartitionResolved, Rejected {}

  private record PartitionResolved(PartitionRestore restore) implements PartitionResolution {}

  /** The log a backup range holds, from its first backup's first log position to its end. */
  private record RangeLog(CheckpointEntry start, CheckpointEntry end) {

    static Optional<RangeLog> of(final BackupMetadata partition, final RangeEntry range) {
      final var start = find(partition.checkpoints(), range.start());
      final var end = find(partition.checkpoints(), range.end());
      return start.isPresent() && end.isPresent()
          ? Optional.of(new RangeLog(start.get(), end.get()))
          : Optional.empty();
    }

    boolean holds(final @Nullable Instant from, final @Nullable Long exportedPosition) {
      final var holdsFrom =
          from == null
              || (!start.checkpointTimestamp().isAfter(from)
                  && !end.checkpointTimestamp().isBefore(from));
      if (exportedPosition == null) {
        return holdsFrom;
      }
      final var firstNeeded = exportedPosition < 0 ? 1 : exportedPosition;
      return holdsFrom && covers(firstNeeded);
    }

    boolean covers(final long position) {
      return start.getFirstLogPositionOrDefault() <= position
          && position <= end.checkpointPosition();
    }
  }
}
