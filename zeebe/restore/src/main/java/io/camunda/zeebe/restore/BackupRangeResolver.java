/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import io.camunda.zeebe.backup.api.BackupIdentifierWildcard;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard.CheckpointPattern;
import io.camunda.zeebe.backup.api.BackupRange;
import io.camunda.zeebe.backup.api.BackupRange.Complete;
import io.camunda.zeebe.backup.api.BackupRanges;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.api.Interval;
import io.camunda.zeebe.util.Either;
import io.camunda.zeebe.util.collection.Tuple;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedCollection;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pure, side-effect-free resolver for determining valid backup ranges for restore operations. All
 * methods are static and accept data as parameters, making them easily testable without mocks.
 */
public final class BackupRangeResolver {

  private static final Logger LOG = LoggerFactory.getLogger(BackupRangeResolver.class);
  private final BackupStore store;

  public BackupRangeResolver(final BackupStore store) {
    this.store = store;
  }

  public PartitionRestoreInfo getInformationPerPartition(
      final int partition, final Instant from, final Instant to, final long lastExportedPosition) {
    final var interval = new Interval<>(from, to);
    final var rangeMarkers = store.rangeMarkers(partition).join();
    final var ranges = BackupRanges.fromMarkers(rangeMarkers);

    final int finalPartition = partition;
    final var statusInterval =
        findBackupsInRange(interval, ranges, partition)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "No backup range found for partition "
                            + finalPartition
                            + " in interval ["
                            + interval.start()
                            + ","
                            + interval.end()
                            + "]"));

    final var backups = findBackupsInInterval(interval, statusInterval.getRight(), finalPartition);

    // Step 4: Find valid safe points for each partition using RDBMS exported positions

    final var safeStart =
        BackupRangeResolver.findSafeStartCheckpoint(lastExportedPosition, backups)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "No safe start checkpoint found for partition "
                            + finalPartition
                            + " with exported position "
                            + lastExportedPosition));

    return new PartitionRestoreInfo(partition, safeStart, statusInterval.getLeft(), backups);
  }

  public BackupStatus toBackupStatus(final int partitionId, final long checkpoint) {
    final var wildcard =
        BackupIdentifierWildcard.forPartition(partitionId, CheckpointPattern.of(checkpoint));
    // TODO check if there's more than one
    final var backup =
        store.list(wildcard).join().stream().filter(BackupStatus::isCompleted).findFirst();
    if (backup.isPresent()) {
      return backup.get();
    } else {
      throw new BackupNotFoundException(checkpoint, partitionId);
    }
  }

  public Optional<Tuple<Complete, Interval<BackupStatus>>> findBackupsInRange(
      final Interval<Instant> interval,
      final SequencedCollection<BackupRange> ranges,
      final int partitionId) {
    // ranges are ordered chronologically, so we start from the latest one, going backwards in time
    for (final var range : ranges.reversed()) {
      if (range instanceof final BackupRange.Complete completeRange) {
        // get the BackupStatuses from the store
        final Interval<BackupStatus> statusInterval =
            completeRange.interval().map(c -> toBackupStatus(partitionId, c));
        final Interval<Instant> timeInterval = statusInterval.map(BackupStatus::createdOrThrow);
        if (timeInterval.contains(interval)) {
          return Optional.of(new Tuple<>(completeRange, statusInterval));
        }
      }
    }
    return Optional.empty();
  }

  public List<BackupStatus> findBackupsInInterval(
      final Interval<Instant> interval,
      final Interval<BackupStatus> statusInterval,
      final int partitionId) {
    final var backups =
        store
            .list(
                BackupIdentifierWildcard.forPartition(
                    partitionId,
                    CheckpointPattern.ofInterval(statusInterval.map(s -> s.id().checkpointId()))))
            .join();

    return backups.stream()
        .filter(bs -> bs.statusCode() == BackupStatusCode.COMPLETED)
        .filter(bs -> interval.contains(bs.createdOrThrow()))
        .toList();
  }

  /** Find the backups before the given timestamp */
  public static Stream<BackupStatus> findLatestBackupsBefore(
      final Instant toTimestamp, final Collection<BackupStatus> allBackups) {
    return allBackups.stream()
        .filter(BackupStatus::isCompleted)
        .filter(
            backup ->
                backup
                    .descriptor()
                    .map(ds -> !ds.checkpointTimestamp().isAfter(toTimestamp))
                    .orElse(false));
  }

  /**
   * Finds the latest backup before the given timestamp.
   *
   * @param toTimestamp the target timestamp
   * @param allBackups collection of all available backups
   * @return the backup with max(checkpointTimestamp) where checkpointTimestamp <= toTimestamp, or
   *     empty if no such backup exists
   */
  public static Optional<BackupStatus> findLatestBackupBefore(
      final Instant toTimestamp, final Collection<BackupStatus> allBackups) {
    return findLatestBackupsBefore(toTimestamp, allBackups)
        .max(Comparator.comparing(backup -> backup.descriptor().get().checkpointTimestamp()));
  }

  /**
   * Finds the safe starting checkpoint for a partition based on its exported position.
   *
   * @param exportedPosition the last position exported to RDBMS for this partition
   * @param availableBackups collection of all available backups
   * @return the largest checkpoint ID where checkpointPosition <= exportedPosition, or empty if no
   *     such checkpoint exists
   */
  public static Optional<Long> findSafeStartCheckpoint(
      final long exportedPosition, final Collection<BackupStatus> availableBackups) {
    return availableBackups.stream()
        .filter(BackupStatus::isCompleted)
        .filter(
            backup ->
                backup
                    .descriptor()
                    .map(ds -> ds.checkpointPosition() <= exportedPosition)
                    .orElse(false))
        .map(backup -> backup.id().checkpointId())
        .max(Long::compareTo);
  }

  /**
   * Validates that all partitions can safely restore to the global checkpoint.
   *
   * <p>This is CRITICAL for cluster consistency: ALL nodes must restore to the SAME global
   * checkpoint ID. However, each partition may start from a different checkpoint based on its
   * export position.
   *
   * <p>The validation ensures:
   *
   * <ul>
   *   <li>We have backup data from ALL expected nodes
   *   <li>Each partition has a safe start checkpoint where checkpointPosition <= exportedPosition
   *   <li>For all partitions, there is a continuous backup ranges from backup_start[partition] to
   *       the global checkpoint
   *   <li>If ANY partition cannot reach the global checkpoint, the entire restore fails. This is
   *       necessary to ensure that all nodes in the cluster restore the same checkpoint.
   * </ul>
   *
   * @param globalCheckpointId the target checkpoint that ALL nodes will restore to
   * @param safeStartByPartition map of partitionId → safe start checkpoint based on RDBMS export
   *     position
   * @param backupsByPartition map of (nodeId, partitionId) → collection of available backups
   * @param rangesByPartition map of partitionId → backup range for validation
   * @return validation result with success if all partitions can reach global checkpoint
   */
  public static Either<String, Void> validateGlobalCheckpointReachability(
      final long globalCheckpointId,
      final Map<Integer, Long> safeStartByPartition,
      final Map<Integer, SequencedCollection<BackupStatus>> backupsByPartition,
      final Map<Integer, BackupRange> rangesByPartition) {

    if (safeStartByPartition.isEmpty()) {
      return Either.left("No partitions found for checkpoint reachability validation");
    }

    final var validator =
        new ReachabilityValidator(
            globalCheckpointId, safeStartByPartition, backupsByPartition, rangesByPartition);

    return validator.validate();
  }

  private static List<BackupStatus> filterAndSortBackups(
      final Collection<BackupStatus> backups, final long safeStart, final long globalCheckpointId) {
    return backups.stream()
        .filter(b -> b.descriptor().isPresent())
        .filter(
            b -> b.id().checkpointId() >= safeStart && b.id().checkpointId() <= globalCheckpointId)
        .sorted(Comparator.comparing(b -> b.id().checkpointId()))
        .toList();
  }

  record PartitionRestoreInfo(
      int partition,
      long safeStart,
      BackupRange completRange,
      SequencedCollection<BackupStatus> backupStatuses) {}

  /** Logic for validating that all partitions can reach the global checkpoint. */
  private static final class ReachabilityValidator {
    private final long globalCheckpointId;
    private final Map<Integer, Long> safeStartByPartition;
    private final Map<Integer, SequencedCollection<BackupStatus>> backupsByPartition;
    private final Map<Integer, BackupRange> rangesByPartition;
    private final List<String> failures = new ArrayList<>();

    private ReachabilityValidator(
        final long globalCheckpointId,
        final Map<Integer, Long> safeStartByPartition,
        final Map<Integer, SequencedCollection<BackupStatus>> backupsByPartition,
        final Map<Integer, BackupRange> rangesByPartition) {
      this.globalCheckpointId = globalCheckpointId;
      this.safeStartByPartition = safeStartByPartition;
      this.backupsByPartition = backupsByPartition;
      this.rangesByPartition = rangesByPartition;
    }

    private Either<String, Void> validate() {
      safeStartByPartition.keySet().forEach(this::validatePartition);

      if (failures.isEmpty()) {
        return Either.right(null);
      }

      return Either.left(
          String.format(
              "Cannot restore to global checkpoint %d. Failures:\n  - %s",
              globalCheckpointId, String.join("\n  - ", failures)));
    }

    private void validatePartition(final int partitionId) {
      final var safeStart = safeStartByPartition.get(partitionId);

      if (safeStart > globalCheckpointId) {
        addFailure(
            "Partition %d: safe start checkpoint %d is beyond global checkpoint %d.",
            partitionId, safeStart, globalCheckpointId);
        return;
      }

      switch (rangesByPartition.get(partitionId)) {
        case null -> addFailure("Partition %d: no backup range found", partitionId);
        case final BackupRange.Incomplete incomplete ->
            addFailure(
                "Partition %d: backup range [%d, %d] has deletions: %s",
                partitionId,
                incomplete.interval().start(),
                incomplete.interval().end(),
                incomplete.deletedCheckpointIds());
        case final BackupRange.Complete complete -> {
          validateRangeCoverage(partitionId, safeStart, complete);
          validateLogPositionContiguity(partitionId, safeStart);
        }
      }
    }

    private void validateRangeCoverage(
        final int partitionId, final long safeStart, final BackupRange.Complete range) {
      if (range.interval().start() > safeStart || range.interval().end() < globalCheckpointId) {
        addFailure(
            "Partition %d: backup range [%d, %d] does not cover required range [%d, %d]",
            partitionId,
            range.interval().start(),
            range.interval().end(),
            safeStart,
            globalCheckpointId);
      }
    }

    private void validateLogPositionContiguity(final int partitionId, final long safeStart) {
      final var backups = backupsByPartition.get(partitionId);

      final var sortedBackups = filterAndSortBackups(backups, safeStart, globalCheckpointId);

      if (sortedBackups.isEmpty()) {
        addFailure(
            "Partition %d has no backups in range [%d, %d]",
            partitionId, safeStart, globalCheckpointId);
        return;
      }

      final var firstCheckpointId = sortedBackups.getFirst().id().checkpointId();
      final var lastCheckpointId = sortedBackups.getLast().id().checkpointId();

      if (firstCheckpointId > safeStart) {
        addFailure(
            "Partition %ds first backup at checkpoint %d is after safe start %d",
            partitionId, firstCheckpointId, safeStart);
        return;
      }

      if (lastCheckpointId < globalCheckpointId) {
        addFailure(
            "Partition %d: last backup at checkpoint %d is before global checkpoint %d",
            partitionId, lastCheckpointId, globalCheckpointId);
        return;
      }

      validateBackupChainContiguity(partitionId, sortedBackups);
    }

    private void validateBackupChainContiguity(
        final int partitionId, final List<BackupStatus> sortedBackups) {

      for (var i = 1; i < sortedBackups.size(); i++) {
        final var prevBackup = sortedBackups.get(i - 1);
        final var currBackup = sortedBackups.get(i);

        final var prevCheckpointPosition = prevBackup.descriptor().get().checkpointPosition();
        final var currFirstLogPosition = currBackup.descriptor().get().firstLogPosition();

        if (currFirstLogPosition.isPresent()
            && currFirstLogPosition.getAsLong() > prevCheckpointPosition + 1) {
          addFailure(
              "Partition %d: has gap in log positions - backup %d ends at position %d, "
                  + "but backup %d starts at position %d (expected %d)",
              partitionId,
              prevBackup.id().checkpointId(),
              prevCheckpointPosition,
              currBackup.id().checkpointId(),
              currFirstLogPosition.getAsLong(),
              prevCheckpointPosition + 1);
          break;
        }
      }
    }

    private void addFailure(final String format, final Object... args) {
      failures.add(String.format(format, args));
    }
  }
}
