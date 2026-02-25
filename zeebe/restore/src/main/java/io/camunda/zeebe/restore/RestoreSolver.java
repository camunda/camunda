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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.stream.Collectors;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

public class RestoreSolver {

  /**
   * Figures out which backups are required for a globally consistent restore that fits the given
   * criteria.
   *
   * @param metadataByPartition the backup metadata for each partition, keyed by partition id
   * @param from the starting timestamp to consider for finding checkpoints, or {@code null} if no
   *     specific starting timestamp is required
   * @param to when provided, the {@link RestorableBackups#globalCheckpointId()} will be as close as
   *     possible. If not provided, {@link RestorableBackups#globalCheckpointId()} will be the
   *     latest common checkpoint.
   * @param exportedPositions the exported position to consider for finding checkpoints, or {@code
   *     null} if no specific position is required
   * @return a {@link RestorableBackups} object containing the required backups for restoration
   */
  public static RestorableBackups solve(
      final List<BackupMetadata> metadataByPartition,
      @Nullable final Instant from,
      @Nullable final Instant to,
      final Map<Integer, Long> exportedPositions) {
    final var restorableCheckpoints =
        findRestorableCheckpoints(metadataByPartition, from, exportedPositions);
    final var commonCheckpoint = findCommonCheckpoint(restorableCheckpoints, exportedPositions, to);
    return findRestorableBackups(restorableCheckpoints, commonCheckpoint);
  }

  /** Finds all backup-type checkpoints that are required to cover the common checkpoint. */
  private static @NonNull RestorableBackups findRestorableBackups(
      final List<RestorableCheckpoints> allRestorableCheckpoints, final long commonCheckpoint) {
    final var backupsByPartitionId = new HashMap<Integer, List<CheckpointEntry>>();
    for (final var restorableCheckpoints : allRestorableCheckpoints) {
      final var backups = new ArrayList<CheckpointEntry>();
      for (final var checkpoint : restorableCheckpoints.checkpoints()) {
        if (checkpoint.checkpointType().shouldCreateBackup()) {
          backups.add(checkpoint);
          if (checkpoint.checkpointId() >= commonCheckpoint) {
            // Found the last required backup that contains the common checkpoint
            break;
          }
        }
      }
      backupsByPartitionId.put(restorableCheckpoints.partitionId(), backups);
    }
    return new RestorableBackups(commonCheckpoint, backupsByPartitionId);
  }

  /**
   * Finds a list of checkpoints for each partition that start from the given timestamp or exported
   * position.
   */
  private static @NonNull List<RestorableCheckpoints> findRestorableCheckpoints(
      final List<BackupMetadata> metadataByPartition,
      final @Nullable Instant from,
      final Map<Integer, Long> exportedPositions) {
    return metadataByPartition.stream()
        .map(
            entry -> {
              final var exportedPosition =
                  exportedPositions != null ? exportedPositions.get(entry.partitionId()) : null;
              return findRestorableCheckpoints(from, exportedPosition, entry);
            })
        .toList();
  }

  /**
   * For a given partition, finds a list of checkpoints that start from the given timestamp or
   * exported position.
   */
  private static @NonNull RestorableCheckpoints findRestorableCheckpoints(
      @Nullable final Instant from,
      @Nullable final Long exportedPosition,
      final BackupMetadata metadata) {
    final var restorableRange = findRestorableRange(from, exportedPosition, metadata);
    final var restorableCheckpoints = findRestorableCheckpoints(metadata, restorableRange);
    return new RestorableCheckpoints(metadata.partitionId(), restorableCheckpoints);
  }

  /**
   * Finds the common checkpoint closest to the given timestamp for all partitions. If no timestamp
   * is provided, uses the latest common checkpoint.
   */
  private static long findCommonCheckpoint(
      final List<RestorableCheckpoints> allRestorableCheckpoints,
      final Map<Integer, Long> exportedPositions,
      final Instant to) {
    final Queue<CheckpointEntry> candidates;
    if (to == null) {
      candidates =
          new LinkedBlockingDeque<>(allRestorableCheckpoints.getFirst().checkpoints().reversed());
    } else {
      candidates =
          allRestorableCheckpoints.getFirst().checkpoints().stream()
              .sorted(
                  Comparator.comparing(o -> Duration.between(to, o.checkpointTimestamp()).abs()))
              .collect(Collectors.toCollection(LinkedBlockingDeque::new));
    }
    CheckpointEntry candidate;
    while ((candidate = candidates.poll()) != null) {
      boolean missing = false;
      for (final var restorableCheckpoints : allRestorableCheckpoints) {
        final var checkpointInfo =
            findCheckpointInfo(restorableCheckpoints.checkpoints(), candidate.checkpointId());
        if (checkpointInfo == null) {
          missing = true;
          break;
        }
        final var exportedPosition =
            exportedPositions != null
                ? exportedPositions.get(restorableCheckpoints.partitionId())
                : null;
        if (exportedPosition != null && checkpointInfo.checkpointPosition() < exportedPosition) {
          missing = true;
          break;
        }
      }
      if (!missing) {
        return candidate.checkpointId();
      }
    }
    throw new IllegalStateException(
        "Could not find common checkpoint across partitions. Restorable checkpoint count per partition: %s"
            .formatted(
                allRestorableCheckpoints.stream()
                    .collect(
                        Collectors.toMap(
                            RestorableCheckpoints::partitionId, rc -> rc.checkpoints().size()))));
  }

  /** Finds all checkpoints that fit within the given range. */
  private static @NonNull List<CheckpointEntry> findRestorableCheckpoints(
      final BackupMetadata metadata, final RestorableRange restorableRange) {
    final var usableCheckpoints = new ArrayList<CheckpointEntry>();
    for (final var checkpointEntry : metadata.checkpoints()) {
      if (checkpointEntry.checkpointPosition() >= restorableRange.start().firstLogPosition()
          && checkpointEntry.checkpointPosition() <= restorableRange.end().checkpointPosition()) {
        usableCheckpoints.add(checkpointEntry);
      }
    }
    return usableCheckpoints;
  }

  /** Finds a range that fits the given {@code from} and {@code exportedPosition} criteria. */
  private static @NonNull RestorableRange findRestorableRange(
      final @Nullable Instant from,
      final @Nullable Long exportedPosition,
      final BackupMetadata metadata) {
    return metadata.ranges().stream()
        .<RestorableRange>mapMulti(
            (range, consumer) -> {
              final var startInfo = findCheckpointInfo(metadata.checkpoints(), range.start());
              final var endInfo = findCheckpointInfo(metadata.checkpoints(), range.end());
              if (startInfo == null || endInfo == null) {
                throw new IllegalStateException(
                    "Could not find checkpoint info for range %s in partition %d. Number of available checkpoints: %d"
                        .formatted(range, metadata.partitionId(), metadata.checkpoints().size()));
              }
              if (from != null && startInfo.checkpointTimestamp().isAfter(from)) {
                return;
              }
              if (exportedPosition != null && startInfo.firstLogPosition() > exportedPosition) {
                return;
              }
              if (exportedPosition != null && endInfo.checkpointPosition() < exportedPosition) {
                return;
              }
              consumer.accept(new RestorableRange(startInfo, endInfo));
            })
        .findAny()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "No usable range found for partition %d with from=%s, exportedPosition=%s. Available ranges: %s"
                        .formatted(
                            metadata.partitionId(), from, exportedPosition, metadata.ranges())));
  }

  /** Binary search for a checkpoint entry in a sorted list of checkpoint entries. */
  private static @Nullable CheckpointEntry findCheckpointInfo(
      final List<CheckpointEntry> list, final long checkpointId) {
    int low = 0;
    int high = list.size() - 1;

    while (low <= high) {
      final int mid = (low + high) >>> 1;
      final var midVal = list.get(mid);
      final int cmp = Long.compare(midVal.checkpointId(), checkpointId);

      if (cmp < 0) {
        low = mid + 1;
      } else if (cmp > 0) {
        high = mid - 1;
      } else {
        return midVal; // checkpoint found
      }
    }
    return null; // key not found
  }

  public record RestorableRange(CheckpointEntry start, CheckpointEntry end) {}

  public record RestorableCheckpoints(int partitionId, List<CheckpointEntry> checkpoints) {}

  public record RestorableBackups(
      long globalCheckpointId, Map<Integer, List<CheckpointEntry>> backupsByPartitionId) {}
}
