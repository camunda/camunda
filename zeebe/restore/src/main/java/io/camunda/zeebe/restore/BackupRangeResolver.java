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
import io.camunda.zeebe.backup.common.CheckpointIdGenerator;
import io.camunda.zeebe.util.collection.Tuple;
import io.camunda.zeebe.util.concurrency.FuturesUtil;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.SequencedCollection;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@code BackupRangeResolver} class provides methods to resolve backup ranges and backup
 * statuses for a given partition, time interval, or exported position. This class interacts with a
 * {@link BackupStore} to fetch and manage backup data required for restoring partitions or
 * determining safe starting checkpoints.
 */
public final class BackupRangeResolver {

  private static final Logger LOG = LoggerFactory.getLogger(BackupRangeResolver.class);
  private final BackupStore store;

  public BackupRangeResolver(final BackupStore store) {
    this.store = store;
  }

  /**
   * @return the {@link PartitionRestoreInfo} for each partition in parallel, using a Virtual Thread
   *     per task
   */
  public CompletableFuture<GlobalRestoreInfo> getRestoreInfoForAllPartitions(
      final Interval<Instant> interval,
      final int partitionCount,
      final Map<Integer, Long> exportedPositions,
      final CheckpointIdGenerator checkpointIdGenerator,
      final Executor executor) {
    // Verify that exportedPositions is available for all partitions
    final var errors =
        IntStream.rangeClosed(1, partitionCount)
            .boxed()
            .flatMap(
                p ->
                    !exportedPositions.containsKey(p)
                        ? Stream.of(
                            "Expected to find exported position for partition %d, but found none"
                                .formatted(p))
                        : Stream.empty())
            .toList();
    if (!errors.isEmpty()) {
      throw new IllegalStateException(String.format("Errors: %s", errors));
    }
    // Get backup ranges from the store for each partition
    return FuturesUtil.parTraverse(
            IntStream.rangeClosed(1, partitionCount).boxed().toList(),
            partition ->
                CompletableFuture.supplyAsync(
                    () ->
                        getInformationPerPartition(
                            partition,
                            interval,
                            exportedPositions.get(partition),
                            checkpointIdGenerator),
                    executor))
        .thenApply(
            restoreInfos -> {
              final var globalCheckpointId = computeGlobalCheckpointId(restoreInfos);
              // validate all partitions can safely restore to the global checkpoint
              PartitionRestoreInfo.validatePartitions(restoreInfos, globalCheckpointId);
              final var backupIdsByPartition =
                  PartitionRestoreInfo.backupIdsByPartition(restoreInfos);
              return new GlobalRestoreInfo(globalCheckpointId, restoreInfos, backupIdsByPartition);
            });
  }

  public PartitionRestoreInfo getInformationPerPartition(
      final int partition,
      final Interval<Instant> interval,
      final long exporterPosition,
      final CheckpointIdGenerator checkpointIdGenerator) {
    final var rangeMarkers = store.rangeMarkers(partition).join();
    final var ranges = BackupRanges.fromMarkers(rangeMarkers);

    // finds the BackupRange that entirely covers the interval `[from, to]`
    final var statusInterval =
        findBackupRangeCoveringInterval(interval, ranges, partition)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "No complete backup range found for partition %d in interval %s, ranges=%s, timeInterval=%s"
                            .formatted(
                                partition,
                                interval,
                                ranges,
                                ranges.stream()
                                    .map(r -> r.timeInterval(checkpointIdGenerator))
                                    .toList())));

    // Retrieve all BackupStatus in the BackupRange
    // note that all backups must be retrieved as we only know the extremes, not every backup inside
    // the range
    final var backups = getAllBackups(interval, statusInterval.getRight(), partition);

    // Find valid safe points using RDBMS exported positions
    final var safeStart =
        findSafeStartCheckpoint(exporterPosition, backups)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "No safe start checkpoint found for partition %d with exported position %d"
                            .formatted(partition, exporterPosition)));

    final var filteredBackups =
        backups.stream().filter(bs -> bs.id().checkpointId() >= safeStart).toList();
    final var restoreInfo =
        new PartitionRestoreInfo(
            partition, safeStart, exporterPosition, statusInterval.getLeft(), filteredBackups);
    LOG.info("Resolved restore info for partition {} = {}", partition, restoreInfo);
    return restoreInfo;
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
        final Interval<Instant> timeInterval;
        try {
          timeInterval = statusInterval.map(bs -> bs.descriptor().get().checkpointTimestamp());
        } catch (final NoSuchElementException e) {
          // ignore this backup
          continue;
        }
        if (timeInterval.contains(interval)) {
          return Optional.of(new Tuple<>(completeRange, statusInterval));
        }
      }
    }
    return Optional.empty();
  }

  public List<BackupStatus> getAllBackups(
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

    final var completedBackupsMap =
        backups.stream()
            .filter(
                bs -> bs.statusCode() == BackupStatusCode.COMPLETED && bs.descriptor().isPresent())
            .collect(
                Collectors.toMap(bs -> bs.id().checkpointId(), Function.identity(), (a, b) -> a));
    final var completedBackups = completedBackupsMap.values().stream().sorted().toList();
    LOG.debug(
        "Found {} completed backups for partition {} in range {}: {}",
        completedBackups.size(),
        partitionId,
        statusInterval,
        completedBackups);
    final var selectedBackups =
        interval.smallestCover(completedBackups, bs -> bs.descriptor().get().checkpointTimestamp());
    LOG.debug(
        "Selected {} backups for partition {} in range {}: {}",
        selectedBackups.size(),
        partitionId,
        statusInterval,
        selectedBackups);
    return selectedBackups;
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
                    // NOTE: we just need to be able to start exporting from a backup whose
                    // snapshot position is <= exportedPosition. However, to do that we would need
                    // to open the backup. It's simpler to just use the checkpointPosition.
                    // We might be able to use `firstLogPosition` as an optimization in the future.
                    .map(ds -> ds.checkpointPosition() <= exportedPosition)
                    .orElse(false))
        .map(backup -> backup.id().checkpointId())
        .max(Long::compareTo);
  }

  /**
   * Computes the global checkpoint ID and validates that all partitions can safely restore to it.
   *
   * <p>This method finds the maximum checkpoint ID that exists across ALL partitions and validates
   * that each partition can restore to it. This is critical for cluster consistency: during
   * restore, all nodes must restore to the same global checkpoint.
   *
   * <p>Computation:
   *
   * <ol>
   *   <li>For each partition, extract the set of checkpoint IDs from its backup statuses
   *   <li>Compute the intersection of all these sets (checkpoints present in every partition)
   *   <li>Return the maximum checkpoint ID from the intersection
   * </ol>
   *
   * <p>Validation ensures:
   *
   * <ul>
   *   <li>Each partition has a safe start checkpoint where checkpointPosition <= exportedPosition
   *   <li>For all partitions, there is a continuous backup range from safe start to the global
   *       checkpoint
   *   <li>If ANY partition cannot reach the global checkpoint, the entire restore fails
   * </ul>
   *
   * @param restoreInfos collection of restore information for all partitions
   * @return the maximum checkpoint ID that is common to all partitions
   * @throws IllegalStateException if no common checkpoint exists or validation fails
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

  /**
   * @param partition The identifier of the partition being restored
   * @param safeStart The highest checkpoint ID that can be used as the start of the backup range
   * @param backupRange The original {@link BackupRange} where {@param backupStatuses} were
   *     extracted from
   * @param backupStatuses The collection of backup statuses to restore for this partition
   */
  public record PartitionRestoreInfo(
      int partition,
      long safeStart,
      long exporterPosition,
      BackupRange backupRange,
      List<BackupStatus> backupStatuses) {

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
                        incomplete.checkpointInterval().start(),
                        incomplete.checkpointInterval().end(),
                        incomplete.deletedCheckpointIds()));
        case final BackupRange.Complete complete -> {
          validateRangeCoverage(globalCheckpointId, complete);
          validateGlobalCheckpointConsistency(globalCheckpointId);
          validateBackupChainOverlaps(partition, backupStatuses);
        }
      }
    }

    public static void validatePartitions(
        final Collection<PartitionRestoreInfo> restoreInfos, final long globalCheckpointId) {
      // Validate all partitions can restore to the global checkpoint
      final var failures = new ArrayList<String>();
      restoreInfos.forEach(
          info -> {
            try {
              info.validate(globalCheckpointId);
            } catch (final RuntimeException e) {
              failures.add(e.getMessage());
            }
          });

      if (!failures.isEmpty()) {
        throw new IllegalStateException(
            "Cannot restore to global checkpoint %d. Failures:\n  - %s"
                .formatted(globalCheckpointId, String.join("\n  - ", failures)));
      }
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
    public static Map<Integer, long[]> backupIdsByPartition(
        final Collection<PartitionRestoreInfo> restoreInfos) {
      return restoreInfos.stream()
          .collect(
              Collectors.toMap(
                  PartitionRestoreInfo::partition,
                  info ->
                      info.backupStatuses().stream()
                          .mapToLong(bs -> bs.id().checkpointId())
                          .distinct()
                          .toArray()));
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
      if (backupStatuses.isEmpty()) {
        throw new IllegalStateException("Partition %d: no backups found".formatted(partition));
      }

      if (backupStatuses.getFirst().id().checkpointId() > safeStart) {
        throw new IllegalStateException(
            "Partition %d: first backup checkpoint %d is after safe start %d."
                .formatted(partition, backupStatuses.getFirst().id().checkpointId(), safeStart));
      }

      final var firstCheckpointId = backupStatuses.getFirst().id().checkpointId();
      if (firstCheckpointId > safeStart) {
        throw new IllegalStateException(
            "Partition %d: first backup at checkpoint %d is after safe start %d"
                .formatted(partition, firstCheckpointId, safeStart));
      }
    }

    private void validateGlobalCheckpointConsistency(final long globalCheckpointId) {
      if (backupStatuses.isEmpty()) {
        throw new IllegalStateException(
            "Partition %d has no backups in range [%d, %d]"
                .formatted(partition, safeStart, globalCheckpointId));
      }

      final var lastBackup = backupStatuses.getLast();
      if (lastBackup.id().checkpointId() != globalCheckpointId) {
        throw new IllegalStateException(
            "Partition %d: last backup checkpoint %d is not equal to global checkpoint %d."
                .formatted(
                    partition, backupStatuses.getLast().id().checkpointId(), globalCheckpointId));
      }

      if (lastBackup.descriptor().get().checkpointPosition() < exporterPosition) {
        throw new IllegalStateException(
            "Partition %d: last backup checkpoint position %d is less than exporter position %d. Try restoring with a larger time range by increasing the `to` parameter."
                .formatted(
                    partition,
                    lastBackup.descriptor().get().checkpointPosition(),
                    exporterPosition));
      }
    }

    private void validateBackupChainOverlaps(
        final int partitionId, final List<BackupStatus> sortedBackups) {

      for (var i = 1; i < sortedBackups.size(); i++) {
        final var prevBackup = sortedBackups.get(i - 1);
        final var currBackup = sortedBackups.get(i);

        final var prevPositionInterval = getLogPositionInterval(prevBackup);
        final var currPositionInterval = getLogPositionInterval(currBackup);

        if (!(currPositionInterval.overlapsWith(
            prevPositionInterval.withEnd(prevPositionInterval.end() + 1)))) {
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

    private static Interval<Long> getLogPositionInterval(final BackupStatus backup) {
      return backup
          .descriptor()
          .flatMap(BackupDescriptor::getLogPositionInterval)
          .orElseThrow(
              () ->
                  new IllegalStateException("No log position interval for backup " + backup.id()));
    }
  }

  public record GlobalRestoreInfo(
      long globalCheckpointId,
      Collection<PartitionRestoreInfo> restoreInfos,
      Map<Integer, long[]> backupsByPartitionId) {}
}
