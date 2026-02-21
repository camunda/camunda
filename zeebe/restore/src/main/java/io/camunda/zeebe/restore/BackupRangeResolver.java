/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.restore;

import io.camunda.zeebe.backup.api.BackupStore;
import io.camunda.zeebe.backup.common.BackupMetadataManifest;
import io.camunda.zeebe.backup.common.BackupMetadataManifest.CheckpointEntry;
import io.camunda.zeebe.backup.common.BackupMetadataManifest.RangeEntry;
import io.camunda.zeebe.util.concurrency.FuturesUtil;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves backup ranges and checkpoint information for restore operations. Reads per-partition
 * JSON manifests from the {@link BackupStore} via a {@link BackupMetadataReader}, reducing the
 * number of API calls to exactly one per partition.
 */
@NullMarked
public final class BackupRangeResolver {

  private static final Logger LOG = LoggerFactory.getLogger(BackupRangeResolver.class);
  private final BackupMetadataReader metadataReader;

  public BackupRangeResolver(final BackupStore store) {
    this.metadataReader = new BackupMetadataReader(store);
  }

  /**
   * Resolves restore information for all partitions in parallel.
   *
   * @return the {@link GlobalRestoreInfo} containing the global checkpoint ID and per-partition
   *     restore info
   */
  public CompletableFuture<GlobalRestoreInfo> getRestoreInfoForAllPartitions(
      @Nullable final Instant from,
      @Nullable final Instant to,
      final int partitionCount,
      final Map<Integer, Long> exportedPositions,
      final Executor executor) {
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

    return FuturesUtil.parTraverse(
            IntStream.rangeClosed(1, partitionCount).boxed().toList(),
            partition ->
                CompletableFuture.supplyAsync(
                    () -> resolvePartition(partition, from, to, exportedPositions.get(partition)),
                    executor))
        .thenApply(BackupRangeResolver::computeGlobalResult);
  }

  /**
   * Resolves a point-in-time restore target to a single backup checkpoint ID.
   *
   * <p>Algorithm:
   *
   * <ol>
   *   <li>Load per-partition manifests (1 API call each, parallelized)
   *   <li>Per partition, find the highest checkpoint ID (any type) with timestamp &lt;= target
   *   <li>Global lower bound = min(per-partition bounds) — handles cross-partition timestamp skew
   *   <li>If the global lower bound is a MARKER, walk backward to the nearest backup-type
   *       checkpoint
   *   <li>Return the resolved checkpoint ID for restore via {@code restore(long backupId, ...)}
   * </ol>
   */
  public CompletableFuture<Long> resolvePointInTime(
      final Instant target, final int partitionCount, final Executor executor) {
    return FuturesUtil.parTraverse(
            IntStream.rangeClosed(1, partitionCount).boxed().toList(),
            partition -> CompletableFuture.supplyAsync(() -> loadManifest(partition), executor))
        .thenApply(manifests -> resolvePointInTimeFromManifests(manifests, target));
  }

  private long resolvePointInTimeFromManifests(
      final List<BackupMetadataManifest> manifests, final Instant target) {
    final var perPartitionBounds =
        manifests.stream()
            .map(
                m ->
                    m.checkpoints().stream()
                        .filter(e -> !e.checkpointTimestamp().isAfter(target))
                        .max(Comparator.comparingLong(CheckpointEntry::checkpointId))
                        .orElseThrow(
                            () ->
                                new IllegalStateException(
                                    "No checkpoint found at or before %s for partition %d"
                                        .formatted(target, m.partitionId()))))
            .toList();

    final var globalLowerBoundId =
        perPartitionBounds.stream()
            .mapToLong(CheckpointEntry::checkpointId)
            .min()
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "No checkpoint found at or before %s on any partition".formatted(target)));

    final var referenceManifest = manifests.getFirst();
    final var globalCheckpoint =
        referenceManifest.checkpoints().stream()
            .filter(e -> e.checkpointId() == globalLowerBoundId)
            .findFirst()
            .orElseThrow();

    if (!"MARKER".equals(globalCheckpoint.checkpointType())) {
      return globalLowerBoundId;
    }

    LOG.info(
        "Checkpoint {} is a MARKER, walking backward to nearest backup-type checkpoint",
        globalLowerBoundId);
    return referenceManifest.checkpoints().stream()
        .filter(e -> e.checkpointId() < globalLowerBoundId)
        .filter(e -> !"MARKER".equals(e.checkpointType()))
        .max(Comparator.comparingLong(CheckpointEntry::checkpointId))
        .map(CheckpointEntry::checkpointId)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "No backup-type checkpoint found before %s (all checkpoints at or before target are MARKERs)"
                        .formatted(target)));
  }

  private PartitionRestoreInfo resolvePartition(
      final int partition,
      @Nullable final Instant from,
      @Nullable final Instant to,
      final long exporterPosition) {
    final var manifest = loadManifest(partition);

    final var timestamps =
        manifest.checkpoints().stream()
            .collect(
                Collectors.toMap(
                    CheckpointEntry::checkpointId, CheckpointEntry::checkpointTimestamp));

    final var range =
        findCoveringRange(manifest.ranges(), timestamps, from, to)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "No complete backup range found for partition %d in interval [%s, %s], ranges=%s"
                            .formatted(partition, from, to, manifest.ranges())));

    final var rangeCheckpoints =
        manifest.checkpoints().stream()
            .filter(e -> !"MARKER".equals(e.checkpointType()))
            .filter(e -> e.checkpointId() >= range.start() && e.checkpointId() <= range.end())
            .sorted(Comparator.comparingLong(CheckpointEntry::checkpointId))
            .toList();

    final var effectiveFrom = from != null ? from : timestamps.get(range.start());
    final var effectiveTo = to != null ? to : timestamps.get(range.end());

    final var selected = selectCheckpoints(rangeCheckpoints, effectiveFrom, effectiveTo);

    final var safeStart =
        findSafeStartCheckpoint(exporterPosition, selected)
            .orElseThrow(
                () ->
                    new IllegalArgumentException(
                        "No safe start checkpoint found for partition %d with exported position %d"
                            .formatted(partition, exporterPosition)));

    final var filtered = selected.stream().filter(e -> e.checkpointId() >= safeStart).toList();

    final var restoreInfo =
        new PartitionRestoreInfo(partition, safeStart, exporterPosition, range, filtered);
    LOG.info("Resolved restore info for partition {} = {}", partition, restoreInfo);
    return restoreInfo;
  }

  /**
   * Finds the range that covers the requested time interval, iterating in reverse chronological
   * order (latest range first).
   */
  private static Optional<RangeEntry> findCoveringRange(
      final List<RangeEntry> ranges,
      final Map<Long, Instant> timestamps,
      @Nullable final Instant from,
      @Nullable final Instant to) {
    for (var i = ranges.size() - 1; i >= 0; i--) {
      final var range = ranges.get(i);
      final var startTime = timestamps.get(range.start());
      final var endTime = timestamps.get(range.end());
      if (startTime == null || endTime == null) {
        continue;
      }

      if (from == null && to == null) {
        return Optional.of(range);
      }

      final var effectiveFrom = from != null ? from : to;
      final var effectiveTo = to != null ? to : from;
      if (!startTime.isAfter(effectiveFrom) && !endTime.isBefore(effectiveTo)) {
        return Optional.of(range);
      }
    }
    return Optional.empty();
  }

  /**
   * Selects the minimal set of checkpoints whose timestamps cover [from, to]. Includes the last
   * checkpoint before {@code from} if no exact match, and the first checkpoint after {@code to} if
   * no exact match.
   */
  private static List<CheckpointEntry> selectCheckpoints(
      final List<CheckpointEntry> sorted, final Instant from, final Instant to) {
    CheckpointEntry lastBefore = null;
    CheckpointEntry firstAfter = null;
    final var within = new ArrayList<CheckpointEntry>();

    for (final var entry : sorted) {
      final var ts = entry.checkpointTimestamp();
      if (ts.isBefore(from)) {
        lastBefore = entry;
      } else if (ts.isAfter(to)) {
        if (firstAfter == null) {
          firstAfter = entry;
        }
      } else {
        within.add(entry);
      }
    }

    final var hasStart =
        lastBefore != null
            || (!within.isEmpty() && !within.getFirst().checkpointTimestamp().isAfter(from));
    final var hasEnd =
        firstAfter != null
            || (!within.isEmpty() && !within.getLast().checkpointTimestamp().isBefore(to));

    if (!hasStart || !hasEnd) {
      return List.of();
    }

    final var result = new ArrayList<CheckpointEntry>();
    if (lastBefore != null
        && (within.isEmpty() || !within.getFirst().checkpointTimestamp().equals(from))) {
      result.add(lastBefore);
    }
    result.addAll(within);
    if (firstAfter != null
        && (within.isEmpty() || !within.getLast().checkpointTimestamp().equals(to))) {
      result.add(firstAfter);
    }
    return result;
  }

  /**
   * Finds the safe starting checkpoint for a partition based on its exported position.
   *
   * @param exportedPosition the last position exported to RDBMS for this partition
   * @param checkpoints available checkpoints sorted by ID
   * @return the largest checkpoint ID where checkpointPosition <= exportedPosition
   */
  public static Optional<Long> findSafeStartCheckpoint(
      final long exportedPosition, final List<CheckpointEntry> checkpoints) {
    return checkpoints.stream()
        .filter(e -> e.checkpointPosition() <= exportedPosition)
        .map(CheckpointEntry::checkpointId)
        .max(Long::compareTo);
  }

  private BackupMetadataManifest loadManifest(final int partition) {
    return metadataReader
        .load(partition)
        .join()
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "No backup metadata manifest found for partition %d".formatted(partition)));
  }

  private static GlobalRestoreInfo computeGlobalResult(
      final Collection<PartitionRestoreInfo> restoreInfos) {
    final var globalCheckpointId =
        restoreInfos.stream()
            .map(
                info ->
                    info.checkpoints().stream()
                        .map(CheckpointEntry::checkpointId)
                        .collect(Collectors.toSet()))
            .reduce(
                (set1, set2) -> {
                  set1.retainAll(set2);
                  return set1;
                })
            .flatMap(common -> common.stream().max(Long::compareTo))
            .orElseThrow(
                () ->
                    new IllegalStateException("No common checkpoint found across all partitions"));

    PartitionRestoreInfo.validatePartitions(restoreInfos, globalCheckpointId);
    final var backupIdsByPartition = PartitionRestoreInfo.backupIdsByPartition(restoreInfos);
    return new GlobalRestoreInfo(globalCheckpointId, restoreInfos, backupIdsByPartition);
  }

  /**
   * @param partition The partition being restored
   * @param safeStart The highest safe checkpoint ID for this partition
   * @param exporterPosition The last exported position for this partition
   * @param range The backup range containing the restore checkpoints
   * @param checkpoints The checkpoints to restore, sorted by ID
   */
  public record PartitionRestoreInfo(
      int partition,
      long safeStart,
      long exporterPosition,
      RangeEntry range,
      List<CheckpointEntry> checkpoints) {

    public void validate(final long globalCheckpointId) {
      if (safeStart > globalCheckpointId) {
        throw new IllegalStateException(
            "Partition %d: safe start checkpoint %d is beyond global checkpoint %d."
                .formatted(partition, safeStart, globalCheckpointId));
      }

      if (range.start() > safeStart || range.end() < globalCheckpointId) {
        throw new IllegalStateException(
            "Partition %d: backup range [%d, %d] does not cover required range [%d, %d]"
                .formatted(partition, range.start(), range.end(), safeStart, globalCheckpointId));
      }

      if (checkpoints.isEmpty()) {
        throw new IllegalStateException("Partition %d: no backups found".formatted(partition));
      }

      if (checkpoints.getFirst().checkpointId() > safeStart) {
        throw new IllegalStateException(
            "Partition %d: first backup checkpoint %d is after safe start %d."
                .formatted(partition, checkpoints.getFirst().checkpointId(), safeStart));
      }

      final var lastCheckpoint = checkpoints.getLast();
      if (lastCheckpoint.checkpointId() != globalCheckpointId) {
        throw new IllegalStateException(
            "Partition %d: last backup checkpoint %d is not equal to global checkpoint %d."
                .formatted(partition, lastCheckpoint.checkpointId(), globalCheckpointId));
      }

      if (lastCheckpoint.checkpointPosition() < exporterPosition) {
        throw new IllegalStateException(
            "Partition %d: last backup checkpoint position %d is less than exporter position %d. Try restoring with a larger time range by increasing the `to` parameter."
                .formatted(partition, lastCheckpoint.checkpointPosition(), exporterPosition));
      }

      validateBackupChainOverlaps(partition, checkpoints);
    }

    public static void validatePartitions(
        final Collection<PartitionRestoreInfo> restoreInfos, final long globalCheckpointId) {
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

    public static Map<Integer, long[]> backupIdsByPartition(
        final Collection<PartitionRestoreInfo> restoreInfos) {
      return restoreInfos.stream()
          .collect(
              Collectors.toMap(
                  PartitionRestoreInfo::partition,
                  info ->
                      info.checkpoints().stream()
                          .mapToLong(CheckpointEntry::checkpointId)
                          .distinct()
                          .toArray()));
    }

    private static void validateBackupChainOverlaps(
        final int partitionId, final List<CheckpointEntry> sorted) {
      for (var i = 1; i < sorted.size(); i++) {
        final var prev = sorted.get(i - 1);
        final var curr = sorted.get(i);
        if (curr.firstLogPosition() > prev.checkpointPosition() + 1) {
          throw new IllegalStateException(
              "Partition %d: has gap in log positions - checkpoint %d @ [%d, %d], checkpoint %d @ [%d, %d]"
                  .formatted(
                      partitionId,
                      prev.checkpointId(),
                      prev.firstLogPosition(),
                      prev.checkpointPosition(),
                      curr.checkpointId(),
                      curr.firstLogPosition(),
                      curr.checkpointPosition()));
        }
      }
    }
  }

  public record GlobalRestoreInfo(
      long globalCheckpointId,
      Collection<PartitionRestoreInfo> restoreInfos,
      Map<Integer, long[]> backupsByPartitionId) {}
}
