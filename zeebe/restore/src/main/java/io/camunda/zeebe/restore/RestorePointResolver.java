/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import static java.util.Objects.requireNonNull;

import io.camunda.zeebe.backup.common.BackupMetadata;
import io.camunda.zeebe.backup.common.BackupMetadata.CheckpointEntry;
import io.camunda.zeebe.dynamic.config.state.PartitionRestore;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Queue;
import java.util.TreeSet;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.ToLongFunction;
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
      final CommonCheckpoint common,
      final @Nullable Instant from,
      final @Nullable Map<Integer, Long> exportedPositions) {
    final var commonCheckpoint = common.checkpointId();
    LOG.atDebug().log(
        "Finding restorable backups with commonCheckpoint={}, partitionCount={}",
        commonCheckpoint,
        allRestorableCheckpoints.size());
    final var partitions = new HashMap<Integer, PartitionRestore>();
    for (final var restorableCheckpoints : allRestorableCheckpoints) {
      final var partitionId = restorableCheckpoints.partitionId();
      final var backups = new ArrayList<CheckpointEntry>();
      for (final var checkpoint : restorableCheckpoints.checkpoints()) {
        if (checkpoint.checkpointType().shouldCreateBackup()) {
          verifyBackupContiguity(partitionId, checkpoint, backups);
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
          backups,
          commonCheckpoint,
          from,
          exportedPositions != null ? exportedPositions.get(partitionId) : null);
      LOG.atDebug()
          .addKeyValue("partition", partitionId)
          .log("Found required backups: requiredBackups={}", backups.size());
      // non-null: findCommonCheckpoint only returns a checkpoint every partition has
      final var restorePoint = requireNonNull(common.entriesByPartition().get(partitionId));
      verifyLogCanEndAtRestorePoint(partitionId, backups, restorePoint);
      partitions.put(
          partitionId,
          new PartitionRestore(
              backups.stream()
                  .map(CheckpointEntry::checkpointId)
                  .collect(Collectors.toCollection(TreeSet::new)),
              OptionalLong.of(restorePoint.checkpointPosition())));
    }
    return new RestorableBackups(commonCheckpoint, partitions);
  }

  /**
   * Discards backups that are not necessary for restoring. A backup is not necessary if it is from
   * before the {@code from} timestamp or before the exported position. Retains at least one backup
   * at or before the common checkpoint, even if it is unnecessary according to these rules. If both
   * {@code from} and {@code exportedPosition} are provided, we only trim based on {@code from}
   * because the user specifically requested it.
   */
  private static void discardUnnecessaryBackups(
      final ArrayList<CheckpointEntry> backups,
      final long commonCheckpoint,
      final @Nullable Instant from,
      final @Nullable Long exportedPosition) {
    // For 'from': remove all backups whose timestamp is before 'from'.
    if (from != null) {
      discardUnnecessaryBackupsBeforeTimestamp(backups, commonCheckpoint, from, exportedPosition);
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
    final var lastBeforeExported =
        lastBackupAtOrBefore(backups, CheckpointEntry::checkpointPosition, exportedPosition);
    if (lastBeforeExported > 0) {
      backups.subList(0, lastBeforeExported).clear();
    }
  }

  /**
   * Removes all backups before the given timestamp, except the last one at or before the common
   * checkpoint. The restore starts from the first remaining backup's snapshot, which must not be
   * ahead of the common checkpoint, and so cannot be the one of a backup after a marker. Throws if
   * the exported position is not covered by the remaining backups.
   */
  private static void discardUnnecessaryBackupsBeforeTimestamp(
      final ArrayList<CheckpointEntry> backups,
      final long commonCheckpoint,
      final @NonNull Instant from,
      final @Nullable Long exportedPosition) {
    final var lastAtCommonCheckpoint =
        lastBackupAtOrBefore(backups, CheckpointEntry::checkpointId, commonCheckpoint);
    var firstRetained = 0;
    while (firstRetained < lastAtCommonCheckpoint
        && backups.get(firstRetained).checkpointTimestamp().isBefore(from)) {
      firstRetained++;
    }
    backups.subList(0, firstRetained).clear();
    if (exportedPosition != null && backups.getFirst().checkpointPosition() > exportedPosition) {
      throw new IllegalStateException(
          String.format(
              "After discarding backups before %s, the exporter position %d is not covered by the first remaining backup %s",
              from, exportedPosition, backups.getFirst()));
    }
  }

  /**
   * The index of the last of the given backups, in ascending order, whose {@code key} is at most
   * {@code bound}, or -1 if none is.
   */
  private static int lastBackupAtOrBefore(
      final List<CheckpointEntry> backups,
      final ToLongFunction<CheckpointEntry> key,
      final long bound) {
    var last = -1;
    while (last + 1 < backups.size() && key.applyAsLong(backups.get(last + 1)) <= bound) {
      last++;
    }
    return last;
  }

  /**
   * Sanity check that the partition's restored log can end at the restore point. The log starts at
   * the first backup's snapshot and continues past the second to last backup, so the restore point
   * must be after that backup, or be the only backup itself, whose snapshot can be ahead of any
   * earlier position.
   */
  private static void verifyLogCanEndAtRestorePoint(
      final int partitionId,
      final List<CheckpointEntry> backups,
      final CheckpointEntry restorePoint) {
    final var position = restorePoint.checkpointPosition();
    final var lastBackupPosition = backups.getLast().checkpointPosition();
    final var lowestPosition =
        backups.size() == 1
            ? lastBackupPosition
            : backups.get(backups.size() - 2).checkpointPosition() + 1;
    if (position < lowestPosition || position > lastBackupPosition) {
      throw new IllegalStateException(
          String.format(
              "Cannot restore partition %d up to checkpoint %d at position %d: it must be in [%d, %d], where a log restored from backups %s can end",
              partitionId,
              restorePoint.checkpointId(),
              position,
              lowestPosition,
              lastBackupPosition,
              backups.stream().map(CheckpointEntry::checkpointId).toList()));
    }
  }

  /**
   * Validates that an ordered list of backups forms a contiguous chain and covers the optional
   * exported position for the partition.
   *
   * @param partitionId the partition id
   * @param backups ordered list of checkpoints
   * @param exportedPosition optional exported position to cover
   */
  public static void validateBackupChain(
      final int partitionId,
      final List<CheckpointEntry> backups,
      final @Nullable Long exportedPosition) {
    validateBackupChain(backups, partitionId, exportedPosition);
  }

  /**
   * Validates that an ordered list of backups forms a contiguous chain and covers the optional
   * exported position for the partition.
   *
   * @param backups ordered list of checkpoints
   * @param partitionId the partition id
   * @param exportedPosition optional exported position to cover
   */
  public static void validateBackupChain(
      final List<CheckpointEntry> backups,
      final int partitionId,
      final @Nullable Long exportedPosition) {
    final var actualBackups =
        backups.stream().filter(cp -> cp.checkpointType().shouldCreateBackup()).toList();
    if (actualBackups.isEmpty()) {
      return;
    }

    for (int i = 1; i < actualBackups.size(); i++) {
      verifyBackupContiguity(partitionId, actualBackups.get(i), actualBackups.get(i - 1));
    }

    if (exportedPosition != null) {
      final var effectiveExportedPosition =
          exportedPosition < 0 ? 1L : exportedPosition.longValue();

      final var firstBackup = actualBackups.getFirst();
      if (firstBackup.firstLogPosition().isPresent()
          && firstBackup.firstLogPosition().getAsLong() > effectiveExportedPosition) {
        throw new IllegalStateException(
            ("Cannot restore: backup %d for partition %d starts at log position %d, "
                    + "after the RDBMS exported position %d.")
                .formatted(
                    firstBackup.checkpointId(),
                    partitionId,
                    firstBackup.firstLogPosition().getAsLong(),
                    effectiveExportedPosition));
      }

      final var lastBackup = actualBackups.getLast();
      if (lastBackup.checkpointPosition() < effectiveExportedPosition) {
        throw new IllegalStateException(
            ("Cannot restore: backup %d for partition %d has checkpoint position %d, "
                    + "below the RDBMS exported position %d.")
                .formatted(
                    lastBackup.checkpointId(),
                    partitionId,
                    lastBackup.checkpointPosition(),
                    effectiveExportedPosition));
      }
    }
  }

  /**
   * Sanity check that the backups are actually contiguous. This should already be the case because
   * they are all from the same range, but it doesn't hurt to double-check.
   */
  private static void verifyBackupContiguity(
      final int partitionId,
      @NonNull final CheckpointEntry checkpoint,
      @NonNull final List<CheckpointEntry> previousBackups) {
    if (previousBackups.isEmpty()) {
      return;
    }
    verifyBackupContiguity(partitionId, checkpoint, previousBackups.getLast());
  }

  private static void verifyBackupContiguity(
      final int partitionId,
      @NonNull final CheckpointEntry checkpoint,
      @NonNull final CheckpointEntry previousBackup) {
    final var previousBackupPosition = previousBackup.checkpointPosition();
    final var checkpointPosition = checkpoint.checkpointPosition();

    if (checkpointPosition < previousBackupPosition) {
      throw new IllegalStateException(
          "Unexpected checkpoint position decrease on partition %d: backup %d has checkpoint "
              .concat("position %d, below backup %d with checkpoint position %d")
              .formatted(
                  partitionId,
                  checkpoint.checkpointId(),
                  checkpointPosition,
                  previousBackup.checkpointId(),
                  previousBackupPosition));
    }

    final var firstLogPosition = checkpoint.getFirstLogPositionOrDefault();
    if (firstLogPosition > previousBackupPosition + 1) {
      throw new IllegalStateException(
          String.format(
              "Unexpected data gaps on partition %d between backup %s with first log position %d and backup %s with checkpoint position %d",
              partitionId,
              checkpoint.checkpointId(),
              firstLogPosition,
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
  private static CommonCheckpoint findCommonCheckpoint(
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
      final var entriesByPartition = new HashMap<Integer, CheckpointEntry>();
      boolean missing = false;
      for (final var restorableCheckpoints : allRestorableCheckpoints) {
        final var exportedPosition =
            exportedPositions != null
                ? exportedPositions.get(restorableCheckpoints.partitionId())
                : null;
        final var entry =
            findEntryForCandidate(
                restorableCheckpoints, candidate.checkpointId(), exportedPosition);
        if (entry == null) {
          missing = true;
          break;
        }
        entriesByPartition.put(restorableCheckpoints.partitionId(), entry);
      }
      if (!missing) {
        LOG.atDebug().log("Found common checkpoint {}", candidate.checkpointId());
        return new CommonCheckpoint(candidate.checkpointId(), entriesByPartition);
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

  /**
   * Returns the partition's entry for the candidate checkpoint, or null if the partition cannot be
   * restored to it.
   */
  private static @Nullable CheckpointEntry findEntryForCandidate(
      final RestorableCheckpoints restorableCheckpoints,
      final long candidateId,
      @Nullable final Long exportedPosition) {
    final var entry = findCheckpointInfo(restorableCheckpoints.checkpoints(), candidateId);
    final String reason;
    if (entry == null) {
      reason = "is not found";
    } else if (!restorableCheckpoints.hasBackupUpTo(candidateId)) {
      reason = "has no backup at or before it to restore from";
    } else if (exportedPosition != null && entry.checkpointPosition() < exportedPosition) {
      reason =
          "has checkpoint position %d, below the required exported position %d"
              .formatted(entry.checkpointPosition(), exportedPosition);
    } else {
      return entry;
    }
    LOG.atTrace()
        .addKeyValue("partition", restorableCheckpoints.partitionId())
        .log("Candidate checkpoint {} {}", candidateId, reason);
    return null;
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

  /**
   * @param checkpoints the partition's checkpoints that can be restored, in ascending order
   */
  public record RestorableCheckpoints(int partitionId, @NonNull List<CheckpointEntry> checkpoints) {

    /**
     * Whether a backup at or before the given checkpoint can be restored from. A restore starts
     * from a backup's snapshot, which can be ahead of a marker taken before that backup.
     */
    boolean hasBackupUpTo(final long checkpointId) {
      final var backups =
          checkpoints.stream()
              .filter(checkpoint -> checkpoint.checkpointType().shouldCreateBackup())
              .toList();
      return lastBackupAtOrBefore(backups, CheckpointEntry::checkpointId, checkpointId) >= 0;
    }
  }

  /**
   * @param globalCheckpointId the checkpoint every partition is restored to, a backup or a marker
   * @param partitions what each partition is restored from, by partition id. Its restored log ends
   *     at the global checkpoint as the partition recorded it.
   */
  public record RestorableBackups(
      long globalCheckpointId, Map<Integer, PartitionRestore> partitions) {}

  /**
   * @param checkpointId the checkpoint every partition is restored to, a backup or a marker
   * @param entriesByPartition the checkpoint as each partition recorded it, by partition id
   */
  private record CommonCheckpoint(
      long checkpointId, Map<Integer, CheckpointEntry> entriesByPartition) {}
}
