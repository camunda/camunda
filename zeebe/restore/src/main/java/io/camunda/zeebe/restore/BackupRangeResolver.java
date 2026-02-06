/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import io.camunda.zeebe.backup.api.BackupDescriptor;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard;
import io.camunda.zeebe.backup.api.BackupIdentifierWildcard.CheckpointPattern;
import io.camunda.zeebe.backup.api.BackupRange;
import io.camunda.zeebe.backup.api.BackupRange.Complete;
import io.camunda.zeebe.backup.api.BackupRanges;
import io.camunda.zeebe.backup.api.BackupStatus;
import io.camunda.zeebe.backup.api.BackupStatusCode;
import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.api.Interval;
import io.camunda.zeebe.util.collection.Tuple;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedCollection;
import java.util.stream.Collectors;

/**
 * The {@code BackupRangeResolver} class provides methods to resolve backup ranges and backup
 * statuses for a given partition, time interval, or exported position. This class interacts with a
 * {@link BackupStore} to fetch and manage backup data required for restoring partitions or
 * determining safe starting checkpoints.
 */
public final class BackupRangeResolver {

  private final BackupStore store;

  public BackupRangeResolver(final BackupStore store) {
    this.store = store;
  }

  public PartitionRestoreInfo getInformationPerPartition(
      final int partition, final Instant from, final Instant to, final long lastExportedPosition) {
    final var interval = new Interval<>(from, to);
    final var rangeMarkers = store.rangeMarkers(partition).join();
    final var ranges = BackupRanges.fromMarkers(rangeMarkers);

    // finds the BackupRange that entirely covers the interval `[from, to]`
    final var statusInterval =
        findBackupRangeCoveringInterval(interval, ranges, partition)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "No backup range found for partition "
                            + partition
                            + " in interval ["
                            + interval.start()
                            + ","
                            + interval.end()
                            + "]"));

    // Retrieve all BackupStatus in the BackupRange
    // note that all backups must be retrieved as we only know the extremes, not every backup inside
    // the range
    final var backups =
        getAllBackupsInBackupInterval(interval, statusInterval.getRight(), partition);

    // Find valid safe points for each partition using RDBMS exported positions
    final var safeStart =
        findSafeStartCheckpoint(lastExportedPosition, backups)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "No safe start checkpoint found for partition "
                            + partition
                            + " with exported position "
                            + lastExportedPosition));

    return new PartitionRestoreInfo(partition, safeStart, statusInterval.getLeft(), backups);
  }

  /**
   * Finds the backup range that completely covers the given time interval.
   *
   * <p>The method iterates through the provided collection of backup ranges in reverse
   * chronological order. For each backup range, it checks if it is a complete backup range and
   * determines if its corresponding interval of backup statuses fully contains the specified time
   * interval. If such a range is found, it is returned along with its corresponding backup status
   * interval. If no range meets the criteria, an empty {@code Optional} is returned.
   *
   * @param interval the target time interval that needs to be covered
   * @param ranges a chronologically ordered collection of backup ranges to search
   * @param partitionId the partition ID used for mapping backup checkpoints to their statuses
   * @return an {@code Optional} containing a tuple of the matching complete backup range and its
   *     corresponding status interval if found; otherwise, an empty {@code Optional}
   */
  public Optional<Tuple<Complete, Interval<BackupStatus>>> findBackupRangeCoveringInterval(
      final Interval<Instant> interval,
      final SequencedCollection<BackupRange> ranges,
      final int partitionId) {
    // ranges are ordered chronologically, so we start from the latest one, going backwards in time
    for (final var range : ranges.reversed()) {
      if (range instanceof final BackupRange.Complete completeRange) {
        // get the BackupStatuses from the store
        final Interval<BackupStatus> statusInterval =
            completeRange.checkpointInterval().map(c -> toBackupStatus(partitionId, c));
        final Interval<Instant> timeInterval = statusInterval.map(BackupStatus::createdOrThrow);
        if (timeInterval.contains(interval)) {
          return Optional.of(new Tuple<>(completeRange, statusInterval));
        }
      }
    }
    return Optional.empty();
  }

  public List<BackupStatus> getAllBackupsInBackupInterval(
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

  private static List<BackupStatus> filterAndSortBackups(
      final Collection<BackupStatus> backups, final long safeStart, final long globalCheckpointId) {
    return backups.stream()
        .filter(b -> b.descriptor().isPresent())
        .filter(
            b -> b.id().checkpointId() >= safeStart && b.id().checkpointId() <= globalCheckpointId)
        .sorted(Comparator.comparing(b -> b.id().checkpointId()))
        .toList();
  }

  private BackupStatus toBackupStatus(final int partitionId, final long checkpoint) {
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

  public record PartitionRestoreInfo(
      int partition,
      long safeStart,
      BackupRange backupRange,
      SequencedCollection<BackupStatus> backupStatuses) {

    public void validate(final long globalCheckpointId) {
      if (safeStart > globalCheckpointId) {
        throw new IllegalStateException(
            "Partition %d: safe start checkpoint %d is beyond global checkpoint %d."
                .formatted(partition, safeStart, globalCheckpointId));
      }

      switch (backupRange) {
        case null ->
            throw new IllegalStateException(
                "Partition %d: no backup range found".formatted(partition));
        case final BackupRange.Incomplete incomplete ->
            throw new IllegalStateException(
                "Partition %d: backup range [%d, %d] has deletions: %s"
                    .formatted(
                        partition,
                        incomplete.checkpontInterval().start(),
                        incomplete.checkpontInterval().end(),
                        incomplete.deletedCheckpointIds()));
        case final BackupRange.Complete complete -> {
          validateRangeCoverage(globalCheckpointId, complete);
          validateLogPositionContiguity(globalCheckpointId);
        }
      }
    }

    private void validateRangeCoverage(
        final long globalCheckpointId, final BackupRange.Complete range) {
      if (range.checkpointInterval().start() > safeStart
          || range.checkpointInterval().end() < globalCheckpointId) {
        throw new IllegalStateException(
            "Partition %d: backup range [%d, %d] does not cover required range [%d, %d]"
                .formatted(
                    partition,
                    range.checkpointInterval().start(),
                    range.checkpointInterval().end(),
                    safeStart,
                    globalCheckpointId));
      }
    }

    private void validateLogPositionContiguity(final long globalCheckpointId) {
      final var sortedBackups = filterAndSortBackups(backupStatuses, safeStart, globalCheckpointId);

      if (sortedBackups.isEmpty()) {
        throw new IllegalStateException(
            "Partition %d has no backups in range [%d, %d]"
                .formatted(partition, safeStart, globalCheckpointId));
      }

      final var firstCheckpointId = sortedBackups.getFirst().id().checkpointId();
      final var lastCheckpointId = sortedBackups.getLast().id().checkpointId();

      if (firstCheckpointId > safeStart) {
        throw new IllegalStateException(
            "Partition %ds first backup at checkpoint %d is after safe start %d"
                .formatted(partition, firstCheckpointId, safeStart));
      }

      if (lastCheckpointId < globalCheckpointId) {
        throw new IllegalStateException(
            "Partition %d: last backup at checkpoint %d is before global checkpoint %d"
                .formatted(partition, lastCheckpointId, globalCheckpointId));
      }

      validateBackupChainOverlaps(partition, sortedBackups);
    }

    private void validateBackupChainOverlaps(
        final int partitionId, final List<BackupStatus> sortedBackups) {

      for (var i = 1; i < sortedBackups.size(); i++) {
        final var prevBackup = sortedBackups.get(i - 1);
        final var currBackup = sortedBackups.get(i);

        final var prevPositionInterval = getLogPositionInterval(prevBackup);
        final var currPositionInterval = getLogPositionInterval(currBackup);

        if (!(prevPositionInterval.overlapsWith(currPositionInterval)
            || currPositionInterval.start() == prevPositionInterval.end() + 1)) {
          throw new IllegalStateException(
              "Partition %d: has gap in log positions - checkpoint %d @ %s, checkpoint %d @ %s"
                  .formatted(
                      partitionId,
                      prevBackup.id().checkpointId(),
                      prevPositionInterval,
                      currBackup.id().checkpointId(),
                      currPositionInterval));
        }
      }
    }

    private Interval<Long> getLogPositionInterval(final BackupStatus backup) {
      return backup
          .descriptor()
          .flatMap(BackupDescriptor::getLogPositionInterval)
          .orElseThrow(
              () ->
                  new IllegalStateException("No log position interval for backup " + backup.id()));
    }
  }

  /** Logic for validating that all partitions can reach the global checkpoint. */
  public static final class ReachabilityValidator {

    private final long globalCheckpointId;
    private final Collection<PartitionRestoreInfo> restoreInfos;
    private final List<String> failures = new ArrayList<>();

    public ReachabilityValidator(final Collection<PartitionRestoreInfo> restoreInfos) {
      if (restoreInfos.isEmpty()) {
        throw new IllegalStateException(
            "No partitions found for checkpoint reachability validation");
      }

      globalCheckpointId = computeGlobalCheckpointId(restoreInfos);
      this.restoreInfos = restoreInfos;
    }

    public long getGlobalCheckpointId() {
      return globalCheckpointId;
    }

    /**
     * Returns the backup IDs to restore for each partition.
     *
     * <p>For each partition, this returns a sorted array of checkpoint IDs that are within the
     * range [safeStart, globalCheckpointId]. Each partition may have different backup IDs based on
     * its safe start position, but all must reach the global checkpoint.
     *
     * @return map from partition ID to sorted array of backup checkpoint IDs
     */
    public Map<Integer, long[]> getBackupIdsByPartition() {
      return restoreInfos.stream()
          .collect(
              Collectors.toMap(
                  PartitionRestoreInfo::partition,
                  info ->
                      info.backupStatuses().stream()
                          .map(bs -> bs.id().checkpointId())
                          .filter(id -> id >= info.safeStart() && id <= globalCheckpointId)
                          .sorted()
                          .mapToLong(Long::longValue)
                          .toArray()));
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
     */
    public void validate() {
      restoreInfos.forEach(
          info -> {
            try {
              info.validate(globalCheckpointId);
            } catch (final RuntimeException e) {
              failures.add(e.getMessage());
              e.printStackTrace();
            }
          });

      if (!failures.isEmpty()) {
        throw new IllegalStateException(
            String.format(
                "Cannot restore to global checkpoint %d. Failures:\n  - %s",
                globalCheckpointId, String.join("\n  - ", failures)));
      }
    }

    /**
     * Computes the global checkpoint ID that is common to all partitions.
     *
     * <p>This method finds the maximum checkpoint ID that exists across ALL partitions. This is
     * critical for cluster consistency: during restore, all nodes must restore to the same global
     * checkpoint. The algorithm works as follows:
     *
     * <ol>
     *   <li>For each partition, extract the set of checkpoint IDs from its backup statuses
     *   <li>Compute the intersection of all these sets (checkpoints present in every partition)
     *   <li>Return the maximum checkpoint ID from the intersection
     * </ol>
     *
     * @param restoreInfos collection of restore information for all partitions
     * @return the maximum checkpoint ID that is common to all partitions
     * @throws IllegalStateException if no common checkpoint exists across all partitions
     */
    public static long computeGlobalCheckpointId(
        final Collection<PartitionRestoreInfo> restoreInfos) {
      return restoreInfos.stream()
          .map(
              info ->
                  info.backupStatuses().stream()
                      .map(bs -> bs.id().checkpointId())
                      .collect(Collectors.toSet()))
          .reduce(
              (set1, set2) -> {
                set1.retainAll(set2);
                return set1;
              })
          .flatMap(commonCheckpoints -> commonCheckpoints.stream().max(Long::compareTo))
          .orElseThrow(
              () -> new IllegalStateException("No common checkpoint found across all partitions"));
    }
  }
}
