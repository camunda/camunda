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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestorePointResolver {

  private static final Logger LOG = LoggerFactory.getLogger(RestorePointResolver.class);

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
  public static @NonNull RestorableBackups resolve(
      @NonNull final List<BackupMetadata> metadataByPartition,
      @Nullable final Instant from,
      @Nullable final Instant to,
      @Nullable final Map<Integer, Long> exportedPositions) {
    LOG.atDebug().log(
        "Resolving restore point with from={}, to={}, exportedPositions={}",
        from,
        to,
        exportedPositions);
    final var restorableCheckpoints =
        findRestorableCheckpointsForPartition(metadataByPartition, from, exportedPositions);
    final var commonCheckpoint = findCommonCheckpoint(restorableCheckpoints, exportedPositions, to);
    final var restorableBackups =
        findRequiredBackups(restorableCheckpoints, commonCheckpoint, from, exportedPositions);
    LOG.atDebug().log(
        "Resolved restore point with globalCheckpointId={}",
        restorableBackups.globalCheckpointId());
    return restorableBackups;
  }

  /** Finds all backup-type checkpoints that are required to cover the common checkpoint. */
  private static @NonNull RestorableBackups findRequiredBackups(
      @NonNull final List<RestorableCheckpoints> allRestorableCheckpoints,
      final long commonCheckpoint,
      final @Nullable Instant from,
      final @Nullable Map<Integer, Long> exportedPositions) {
    LOG.atDebug().log(
        "Finding restorable backups with commonCheckpoint={}, partitionCount={}",
        commonCheckpoint,
        allRestorableCheckpoints.size());
    final var backupsByPartitionId = new HashMap<Integer, List<CheckpointEntry>>();
    for (final var restorableCheckpoints : allRestorableCheckpoints) {
      final var partitionId = restorableCheckpoints.partitionId();
      final var backups = new ArrayList<CheckpointEntry>();
      for (final var checkpoint : restorableCheckpoints.checkpoints()) {
        if (checkpoint.checkpointType().shouldCreateBackup()) {
          verifyBackupContiguity(checkpoint, backups);
          backups.add(checkpoint);
          if (checkpoint.checkpointId() >= commonCheckpoint) {
            // Found the last required backup that contains the common checkpoint
            LOG.atTrace()
                .addKeyValue("partition", partitionId)
                .log("Reached commonCheckpoint at checkpointId={}", checkpoint.checkpointId());
            break;
          }
        }
      }
      if (backups.getLast().checkpointId() < commonCheckpoint) {
        throw new IllegalStateException(
            String.format(
                "Last restorable backup %d for partition %d does not cover the common checkpoint %d",
                backups.getLast().checkpointId(), partitionId, commonCheckpoint));
      }
      discardUnnecessaryBackups(
          backups, from, exportedPositions != null ? exportedPositions.get(partitionId) : null);
      LOG.atDebug()
          .addKeyValue("partition", partitionId)
          .log("Found required backups: requiredBackups={}", backups.size());
      backupsByPartitionId.put(partitionId, backups);
    }
    return new RestorableBackups(commonCheckpoint, backupsByPartitionId);
  }

  /**
   * Discards backups that are not necessary for restoring. A backup is not necessary if it is from
   * before the {@code from} timestamp or before the exported position. Retains at least one backup,
   * even if it is unnecessary according to these rules. If both {@code from} and {@code
   * exportedPosition} are provided, we only trim based on {@code from} because the user
   * specifically requested it.
   */
  private static void discardUnnecessaryBackups(
      final ArrayList<CheckpointEntry> backups,
      final @Nullable Instant from,
      final @Nullable Long exportedPosition) {
    // For 'from': remove all backups whose timestamp is before 'from'.
    if (from != null) {
      discardUnnecessaryBackupsBeforeTimestamp(backups, from, exportedPosition);
    }

    // For 'exportedPosition': keep the last backup whose position is below exportedPosition and
    // remove everything before it.
    else if (exportedPosition != null) {
      discardUnnecessaryBackupsBelowExporterPosition(backups, exportedPosition);
    }
  }

  /**
   * Retains the last backup whose position is below the exported position. Discards all previous
   * backups because they are not needed for resuming exporting after restore.
   */
  private static void discardUnnecessaryBackupsBelowExporterPosition(
      final ArrayList<CheckpointEntry> backups, final @NonNull Long exportedPosition) {
    int lastBeforeExported = -1;
    for (int i = 0; i < backups.size(); i++) {
      if (backups.get(i).checkpointPosition() <= exportedPosition) {
        lastBeforeExported = i;
      } else {
        break;
      }
    }
    if (lastBeforeExported > 0) {
      backups.subList(0, lastBeforeExported).clear();
    }
  }

  /**
   * Removes all backups before the given timestamp. Throws if the exported position is not covered
   * by the remaining backups.
   */
  private static void discardUnnecessaryBackupsBeforeTimestamp(
      final ArrayList<CheckpointEntry> backups,
      final @NonNull Instant from,
      final @Nullable Long exportedPosition) {
    final var iterator = backups.iterator();
    while (iterator.hasNext()) {
      final var checkpoint = iterator.next();
      if (!checkpoint.checkpointTimestamp().isBefore(from)) {
        break;
      }
      // Always retain at least one backup.
      if (iterator.hasNext()) {
        iterator.remove();
      }
    }
    if (exportedPosition != null && backups.getFirst().checkpointPosition() > exportedPosition) {
      throw new IllegalStateException(
          String.format(
              "After discarding backups before %s, the exporter position %d is not covered by the first remaining backup %s",
              from, exportedPosition, backups.getFirst()));
    }
  }

  /**
   * Sanity check that the backups are actually contiguous. This should already be the case because
   * they are all from the same range, but it doesn't hurt to double-check.
   */
  private static void verifyBackupContiguity(
      @NonNull final CheckpointEntry checkpoint,
      @NonNull final List<CheckpointEntry> previousBackups) {
    if (previousBackups.isEmpty()) {
      return;
    }

    final var firstLogPosition = checkpoint.getFirstLogPositionOrDefault();
    final var previousBackup = previousBackups.getLast();
    final var previousBackupPosition = previousBackup.checkpointPosition();
    if (firstLogPosition > previousBackupPosition + 1) {
      throw new IllegalStateException(
          String.format(
              "Unexpected data gaps between backup %s with first log position %d and backup %s with checkpoint position %d",
              checkpoint.checkpointId(),
              checkpoint.checkpointPosition(),
              previousBackup.checkpointId(),
              previousBackupPosition));
    }
  }

  /**
   * Finds a list of checkpoints for each partition that start from the given timestamp or exported
   * position.
   */
  private static @NonNull List<RestorableCheckpoints> findRestorableCheckpointsForPartition(
      @NonNull final List<BackupMetadata> metadataByPartition,
      @Nullable final Instant from,
      @Nullable final Map<Integer, Long> exportedPositions) {
    return metadataByPartition.stream()
        .map(
            entry -> {
              final var exportedPosition =
                  exportedPositions != null ? exportedPositions.get(entry.partitionId()) : null;
              return findRestorableCheckpointsForPartition(from, exportedPosition, entry);
            })
        .toList();
  }

  /**
   * For a given partition, finds a list of checkpoints that start from the given timestamp or
   * exported position.
   */
  private static @NonNull RestorableCheckpoints findRestorableCheckpointsForPartition(
      @Nullable final Instant from,
      @Nullable final Long exportedPosition,
      @NonNull final BackupMetadata metadata) {
    final var restorableRange = findRestorableRange(from, exportedPosition, metadata);
    final var restorableCheckpoints = findRestorableCheckpointsInRange(metadata, restorableRange);
    return new RestorableCheckpoints(metadata.partitionId(), restorableCheckpoints);
  }

  /**
   * Finds the common checkpoint closest to the given timestamp for all partitions. If no timestamp
   * is provided, uses the latest common checkpoint.
   */
  private static long findCommonCheckpoint(
      @NonNull final List<RestorableCheckpoints> allRestorableCheckpoints,
      @Nullable final Map<Integer, Long> exportedPositions,
      @Nullable final Instant to) {
    LOG.atDebug().log(
        "Finding common checkpoint closest to {} which covers exported positions {}",
        to,
        exportedPositions);
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
    LOG.atTrace().log("Evaluating {} candidates", candidates.size());
    CheckpointEntry candidate;
    while ((candidate = candidates.poll()) != null) {
      LOG.atTrace().log("Evaluating common checkpoint candidate {}", candidate.checkpointId());
      boolean missing = false;
      for (final var restorableCheckpoints : allRestorableCheckpoints) {
        final var checkpointInfo =
            findCheckpointInfo(restorableCheckpoints.checkpoints(), candidate.checkpointId());
        if (checkpointInfo == null) {
          LOG.atTrace()
              .addKeyValue("partition", restorableCheckpoints.partitionId())
              .log("Candidate checkpoint {} not found", candidate.checkpointId());
          missing = true;
          break;
        }
        final var exportedPosition =
            exportedPositions != null
                ? exportedPositions.get(restorableCheckpoints.partitionId())
                : null;
        if (exportedPosition != null && checkpointInfo.checkpointPosition() < exportedPosition) {
          LOG.atTrace()
              .addKeyValue("partition", restorableCheckpoints.partitionId())
              .log(
                  "Candidate checkpoint {} has checkpoint position {}, below the required exported position {}",
                  candidate.checkpointId(),
                  checkpointInfo.checkpointPosition(),
                  exportedPosition);
          missing = true;
          break;
        }
      }
      if (!missing) {
        LOG.atDebug().log("Found common checkpoint {}", candidate.checkpointId());
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
  private static @NonNull List<CheckpointEntry> findRestorableCheckpointsInRange(
      @NonNull final BackupMetadata metadata, @NonNull final RestorableRange restorableRange) {
    return metadata.checkpoints().stream()
        .filter(
            checkpointEntry -> {
              final var checkpointPosition = checkpointEntry.checkpointPosition();
              return (checkpointPosition >= restorableRange.start().getFirstLogPositionOrDefault())
                  && (checkpointPosition <= restorableRange.end().checkpointPosition());
            })
        .collect(Collectors.toCollection(ArrayList::new));
  }

  /** Finds a range that fits the given {@code from} and {@code exportedPosition} criteria. */
  private static @NonNull RestorableRange findRestorableRange(
      @Nullable final Instant from,
      @Nullable final Long exportedPosition,
      @NonNull final BackupMetadata metadata) {
    // An exported position of -1 means nothing has been exported yet, so the backup needs to
    // start from position 1 (the first exportable log entry).
    final Long effectiveExportedPosition =
        exportedPosition != null && exportedPosition < 0 ? Long.valueOf(1L) : exportedPosition;
    LOG.atDebug()
        .addKeyValue("partition", metadata.partitionId())
        .log(
            "Finding restorable range covering {} and exported position {} in {} available ranges",
            from,
            effectiveExportedPosition,
            metadata.ranges().size());
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
                LOG.atInfo()
                    .addKeyValue("partition", metadata.partitionId())
                    .log(
                        "Skipping range [{}, {}] because it starts at {}, after requested time {}",
                        range.start(),
                        range.end(),
                        startInfo.checkpointTimestamp(),
                        from);
                return;
              }
              if (effectiveExportedPosition != null
                  && startInfo.getFirstLogPositionOrDefault() > effectiveExportedPosition) {
                LOG.atInfo()
                    .addKeyValue("partition", metadata.partitionId())
                    .log(
                        "Skipping range [{}, {}] because the first log position {} is after required exported position {}",
                        range.start(),
                        range.end(),
                        startInfo.firstLogPosition(),
                        effectiveExportedPosition);
                return;
              }
              if (effectiveExportedPosition != null
                  && endInfo.checkpointPosition() < effectiveExportedPosition) {
                LOG.atInfo()
                    .addKeyValue("partition", metadata.partitionId())
                    .log(
                        "Skipping range [{}, {}] because the last log position {} is before the required exported position {}",
                        range.start(),
                        range.end(),
                        endInfo.checkpointPosition(),
                        effectiveExportedPosition);
                return;
              }
              LOG.atDebug()
                  .addKeyValue("partition", metadata.partitionId())
                  .log("Found restorable range [{}, {}]", range.start(), range.end());
              consumer.accept(new RestorableRange(startInfo, endInfo));
            })
        .findAny()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "No usable range found for partition %d with from=%s, exportedPosition=%s. Available ranges: %s"
                        .formatted(
                            metadata.partitionId(),
                            from,
                            effectiveExportedPosition,
                            metadata.ranges())));
  }

  /** Binary search for a checkpoint entry in a sorted list of checkpoint entries. */
  private static @Nullable CheckpointEntry findCheckpointInfo(
      @NonNull final List<CheckpointEntry> list, final long checkpointId) {
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

  public record RestorableRange(@NonNull CheckpointEntry start, @NonNull CheckpointEntry end) {}

  public record RestorableCheckpoints(
      int partitionId, @NonNull List<CheckpointEntry> checkpoints) {}

  public record RestorableBackups(
      long globalCheckpointId,
      @NonNull Map<Integer, @NonNull List<CheckpointEntry>> backupsByPartitionId) {}
}
